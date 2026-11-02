"""Vectorized offline registration for systematic scans; no experimental writes."""
import numpy as np
from intake_143_step_study import robust_fit


def position_scatter(raw,times):
    """Robust interpolation residuals; no feeding edits or quality labels used."""
    left=np.diff(times)[:-1];right=np.diff(times)[1:]
    a=right/(left+right);b=left/(left+right)
    z=(raw[1:-1]-a*raw[:-2]-b*raw[2:])/np.sqrt(1+a*a+b*b)
    z=z[np.isfinite(z)]
    return max(.08,float(1.4826*np.median(np.abs(z-np.median(z))))) if len(z)>=8 else .3


def profiles(rgb,columns,locations):
    """One or many row vectors -> median RGB profiles across selected columns."""
    rows=np.atleast_2d(locations)
    valid=np.all(np.isfinite(rows)&(rows>=0)&(rows<=rgb.shape[0]-1),axis=1)
    safe=np.clip(np.nan_to_num(rows),0,rgb.shape[0]-1)
    lo=np.floor(safe).astype(int);hi=np.minimum(lo+1,rgb.shape[0]-1);f=(safe-lo)[:,:,None,None]
    columns=np.asarray(columns)
    a=rgb[lo[:,:,None],columns[None,None,:],:]
    b=rgb[hi[:,:,None],columns[None,None,:],:]
    return np.median(a*(1-f)+b*f,axis=2),valid


def correlations(template,candidates):
    a=template-template.mean(axis=0)
    b=candidates-candidates.mean(axis=1,keepdims=True)
    num=np.einsum('rc,src->s',a,b)
    denom=np.linalg.norm(a)*np.sqrt(np.sum(b*b,axis=(1,2)))
    return np.divide(num,denom,out=np.zeros_like(num),where=denom>1e-10)


def match(rgb,columns,rows,template,expected,radius,grid):
    shifts=np.arange(expected-radius,expected+radius+.001,grid)
    candidates,valid=profiles(rgb,columns,rows[None,:]+shifts[:,None])
    intensity=correlations(template,candidates)
    gradient=correlations(np.diff(template,axis=0),np.diff(candidates,axis=1))
    scores=.5*(intensity+gradient);scores[~valid]=-1
    j=int(np.argmax(scores))
    return dict(shift=float(shifts[j]),score=float(scores[j]),profile=candidates[j],index=j,shifts=shifts,scores=scores,
                intensity=float(intensity[j]),gradient=float(gradient[j]))


def stationary_edges(rgb,threshold=6.,fraction=.8):
    # Persistent gradient rows are warnings, not a physical tape classifier.
    gradient=np.sqrt(np.mean(np.diff(rgb.astype(float),axis=0)**2,axis=2))
    return np.flatnonzero(np.mean(gradient>=threshold,axis=1)>=fraction)+.5


def register(rgb,raw,times,first,noise,flank=5,half_width=8,details=False):
    before=np.arange(first-flank,first);after=np.arange(first,first+flank)
    if before[0]<0 or after[-1]>=len(raw):return None
    indices=np.r_[before,after]
    if not np.all(np.isfinite(raw[indices])):return None
    boundary=.5*(times[first-1]+times[first])
    centre=float(np.median(raw[before]));expected=float(np.median(raw[after])-centre)
    rows=centre+np.arange(-half_width,half_width+1)
    p,valid=profiles(rgb,before,rows)
    if not valid[0]:return None
    template=p[0]
    m=match(rgb,after,rows,template,expected,5.,.05)
    within=[];within_scores=[];within_limit=False
    for columns,anchor,locations in [(before,template,rows),(after,m['profile'],rows+m['shift'])]:
        seed_centre=np.median(raw[columns])
        for column in columns:
            q=match(rgb,[column],locations,anchor,raw[column]-seed_centre,2.,.05)
            within.append(q['shift']);within_scores.append(q['score'])
            within_limit|=q['index']<2 or q['index']>len(q['shifts'])-3
    fit=robust_fit(np.array(within),times[indices],boundary)
    dt=float(np.median(times[after])-np.median(times[before]))
    amount=m['shift']-fit['slope']*dt
    slopes=[];sxx=[]
    for ix,positions in [(before,within[:flank]),(after,within[flank:])]:
        t=times[ix]-np.mean(times[ix]);sxx.append(float(t@t));slopes.append(float(t@np.array(positions)/(t@t)))
    tolerance=3*noise*np.sqrt(1/sxx[0]+1/sxx[1])
    alternatives=m['scores'][np.abs(m['shifts']-m['shift'])>1.]
    margin=m['score']-float(alternatives.max()) if len(alternatives) else 0.
    near=m['shifts'][m['scores']>=m['score']-.005]
    reasons=[]
    if m['score']<.9:reasons.append('weak_profile_match')
    if min(within_scores)<.85:reasons.append('weak_within_flank_match')
    if abs(slopes[1]-slopes[0])>tolerance:reasons.append('flank_drifts_disagree')
    if m['index']<2 or m['index']>len(m['shifts'])-3:reasons.append('search_boundary')
    if within_limit:reasons.append('within_flank_search_boundary')
    if margin<.01:reasons.append('broad_or_competing_match')
    grad=np.linalg.norm(np.diff(template,axis=0),axis=1);peak=int(np.argmax(grad))
    feature=float((rows[peak]+rows[peak+1])/2)
    observed=m['shift']+within[flank]-within[flank-1]-fit['slope']*(times[first]-times[first-1])
    design=np.column_stack([np.ones(len(indices)),times[indices]-boundary,(times[indices]>boundary).astype(float)])
    factor=float(np.sqrt(np.linalg.pinv(design.T@design)[2,2]))
    denominator=max(.08,fit['residualSigma'])*factor
    result=dict(first=first,flank=flank,halfWidth=half_width,imageShift=m['shift'],step=float(amount),
                score=m['score'],normalisedStep=float(amount/denominator),scale=float(denominator),fitSigma=fit['residualSigma'],
                localDrift=fit['slope'],observedBoundary=float(observed),featureBefore=feature,featureAfter=feature+m['shift'],
                featureAtBoundary=float(feature+fit['slope']*(boundary-np.median(times[before]))),
                driftDifference=slopes[1]-slopes[0],driftTolerance=float(tolerance),nearBestWidth=float(near[-1]-near[0]),
                alternativeMargin=margin,reasons=reasons)
    if details:
        result['profile']=[[float(row),*a.tolist(),*b.tolist()] for row,a,b in zip(rows,template,m['profile'])]
        result['scan']=[[float(s),float(v)] for s,v in zip(m['shifts'],m['scores'])]
    return result
