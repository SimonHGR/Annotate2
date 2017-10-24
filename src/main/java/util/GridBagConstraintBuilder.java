package util;

import java.awt.GridBagConstraints;
import java.awt.Insets;

public class GridBagConstraintBuilder {
    private int columnCount = 1;
    private int gridx = 1, gridy = 1;
    private int anchor = GridBagConstraints.CENTER;
    private int fill = GridBagConstraints.BOTH;
    private int gridheight = 1, gridwidth = 1;
    private int weightx = 1, weighty = 0;
    private Insets insets = new Insets(2, 2, 2, 2);
    private int ipadx = 0, ipady = 0;

    public GridBagConstraintBuilder() {
    }

    public GridBagConstraintBuilder(int columns) {
        this.columnCount = columns + 1;
    }

    public GridBagConstraintBuilder(
            int gridx, int gridy,
            int gridwidth, int gridheight,
            int weightx, int weighty,
            int anchor, int fill,
            Insets insets,
            int ipadx, int ipady
    ) {
            this.gridx = gridx;
            this.gridy = gridy;
            this.gridwidth = gridwidth;
            this.gridheight = gridheight;
            this.weightx = weightx;
            this.weighty = weighty;
            this.anchor = anchor;
            this.fill = fill;
            this.insets = insets;
            this.ipadx = ipadx;
            this.ipady = ipady;
    }

    public GridBagConstraintBuilder nextX() {
        gridx += gridwidth;
        if (gridx >= columnCount) {
            nextY();
        }
        return this;
    }
    
    public GridBagConstraintBuilder nextY() {
        ++gridy;
        gridx = 1;
        return this;
    }

    public GridBagConstraintBuilder fullWidth() {
        gridwidth = columnCount;
        return this;
    }
        
    public GridBagConstraintBuilder singleWidth() {
        gridwidth = 1;
        return this;
    }
    
    public GridBagConstraints build() {
        return new GridBagConstraints(
                gridx, gridy,
                gridwidth, gridheight,
                weightx, weighty,
                anchor, fill,
                insets,
                ipadx, ipady);
    }
}
