__kernel void multiply2arrays(__global float* a, __global float* b, __global float* output)
{
	int index = get_global_id(0);
	output[index] = a[index] * b[index];
}

__kernel void convolve2D(__global float* input, int inputWidth, int inputHeight,
                         __global float* k, int kWidth, int kHeight,
                         __global float* output)
{
	int pixel = get_global_id(0);
	int inX, inY, inXY = 0, kXY = 0;
	float iSum = 0.f;
	const int x = pixel % inputWidth;
	const int y = pixel / inputWidth;
	
	for (int kY = -kHeight; kY <= kHeight; kY++) {
		inY = y + kY;
		if (inY < 0 || inY >= inputHeight) continue;
		inXY = inY * inputWidth;
		for (int kX = -kWidth; kX <= kWidth; kX++, kXY++) {
			inX = x + kX;
			if (inX < 0 || inX >= inputWidth) continue;
			iSum += input[inXY + inX] * k[kXY];
		}
	}
	output[pixel] = iSum;
}

__kernel void convolve2D_mirror(__global float* input, int inputWidth, int inputHeight,
                   		        __global float* k, int kWidth, int kHeight,
                   		        __global float* output)
{
	int pixel = get_global_id(0);
	int inX, inY, inXY = 0, kXY = 0;
	float iSum = 0.f;
	const int x = pixel % inputWidth;
	const int y = pixel / inputWidth;
	
	for (int kY = -kHeight; kY <= kHeight; kY++) {
		inY = y + kY;
		if (inY < 0) inY = -inY + 1;
		else if (inY >= inputHeight) inY = (inputHeight << 1) - inY - 1;
		inXY = inY * inputWidth;
		for (int kX = -kWidth; kX <= kWidth; kX++, kXY++) {
			inX = x + kX;
			if (inX < 0) inX = -inX + 1;
			else if (inX >= inputWidth) inX = (inputWidth << 1) - inX - 1;
			iSum += input[inXY + inX] * k[kXY];
		}
	}
	output[pixel] = iSum;
}

// Expects INVERSE Affine Transform Matrix M s.t. P_in = M * P_out
// m00 m01 m02 (row 0)
// m10 m11 m12 (row 1)
__kernel void affineTransform2D(__global float* input, int inputWidth, int inputHeight,
                                float m00, float m01, float m02,
                                float m10, float m11, float m12,
                                __global float* output)
{
	int pixel = get_global_id(0);
	const int x = pixel % inputWidth;
	const int y = pixel / inputWidth;
	
	// Map Output(x,y) -> Input(srcX, srcY)
	float srcX = m00 * x + m01 * y + m02;
	float srcY = m10 * x + m11 * y + m12;
	
	int x0 = (int)floor(srcX);
	int y0 = (int)floor(srcY);
	
	// Check bounds
	if (x0 < 0 || y0 < 0 || x0 >= inputWidth - 1 || y0 >= inputHeight - 1) {
		output[pixel] = 0.0f;
		return;
	}
	
	int x1 = x0 + 1;
	int y1 = y0 + 1;
	float fx = srcX - x0;
	float fy = srcY - y0;
	
	int idx00 = y0 * inputWidth + x0;
	int idx10 = y0 * inputWidth + x1;
	int idx01 = y1 * inputWidth + x0;
	int idx11 = y1 * inputWidth + x1;
	
	float val0 = input[idx00] * (1.0f - fx) + input[idx10] * fx;
	float val1 = input[idx01] * (1.0f - fx) + input[idx11] * fx;
	output[pixel] = val0 * (1.0f - fy) + val1 * fy;
}

// Expects INVERSE Homography Matrix H s.t. P_in = H * P_out
__kernel void perspectiveTransform2D(__global float* input, int inputWidth, int inputHeight,
                                     float h0, float h1, float h2,
                                     float h3, float h4, float h5,
                                     float h6, float h7, float h8,
                                     __global float* output)
{
	int pixel = get_global_id(0);
	const int x = pixel % inputWidth;
	const int y = pixel / inputWidth;
	
	float z = h6 * x + h7 * y + h8;
	
	if (fabs(z) < 1e-9f) {
		output[pixel] = 0.0f;
		return;
	}
	
	float srcX = (h0 * x + h1 * y + h2) / z;
	float srcY = (h3 * x + h4 * y + h5) / z;
	
	int x0 = (int)floor(srcX);
	int y0 = (int)floor(srcY);
	
	if (x0 < 0 || y0 < 0 || x0 >= inputWidth - 1 || y0 >= inputHeight - 1) {
		output[pixel] = 0.0f;
		return;
	}

	int x1 = x0 + 1;
	int y1 = y0 + 1;
	float fx = srcX - x0;
	float fy = srcY - y0;
	
	int idx00 = y0 * inputWidth + x0;
	int idx10 = y0 * inputWidth + x1;
	int idx01 = y1 * inputWidth + x0;
	int idx11 = y1 * inputWidth + x1;
	
	float val0 = input[idx00] * (1.0f - fx) + input[idx10] * fx;
	float val1 = input[idx01] * (1.0f - fx) + input[idx11] * fx;
	output[pixel] = val0 * (1.0f - fy) + val1 * fy;
}