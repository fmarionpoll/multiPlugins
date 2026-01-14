package plugins.fmp.multiSPOTS96.dlg.b_spots;


import java.awt.Color;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

import plugins.fmp.multiSPOTS96.MultiSPOTS96;

// https://www.youtube.com/watch?v=VlmyTOvvuJc

public class CreateSpotsArrayPanel extends JPanel implements ActionListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	JToggleButton carre = new JToggleButton("essai");
	MultiSPOTS96 parent0 = null;
	JButton[][] butArray = null;
	int ncolumns = 1;
	int nrows = 1;
	

	public void initialize(MultiSPOTS96 parent0, int ncolumns, int nrows) {
		this.parent0 = parent0;
		this.ncolumns = ncolumns;
		this.nrows = nrows;
		
		JPanel gridPanel = new JPanel();
		gridPanel.setLayout(new GridLayout(ncolumns, nrows));
		butArray = new JButton[nrows][ncolumns];
		for (int r = 0; r < nrows; r++)
			for (int c = 0; c < ncolumns; c++) {
				butArray[r][c] = new JButton();
				butArray[r][c].setBackground(new Color(20 + r*2, 2 *40+c, 255-r*10));
				butArray[r][c].addActionListener(this);
				butArray[r][c].setSize(50, 50);
				gridPanel.add(butArray[r][c]);
			}
		validate();
	}


	@Override
	public void actionPerformed(ActionEvent e) {
		for (int r = 0; r < nrows; r++)
			for (int c = 0; c < ncolumns; c++) {
				if (e.getSource()== butArray[r][c])
					butArray[r][c].setBackground(Color.red);
			}
		
	}
}
