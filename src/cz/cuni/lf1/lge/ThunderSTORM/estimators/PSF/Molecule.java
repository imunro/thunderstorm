package cz.cuni.lf1.lge.ThunderSTORM.estimators.PSF;

import cz.cuni.lf1.lge.ThunderSTORM.estimators.PSF.MoleculeDescriptor.Units;
import static cz.cuni.lf1.lge.ThunderSTORM.estimators.PSF.PSFModel.Params;
import static cz.cuni.lf1.lge.ThunderSTORM.util.Math.sqr;
import ij.IJ;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

public final class Molecule implements Comparable<Molecule> {

    public MoleculeDescriptor descriptor;
    public Vector<Molecule> detections;
    private boolean sortedDetections;
    public Vector<Double> values;

    public Molecule(MoleculeDescriptor descriptor, Vector<Double> values) {
        this.descriptor = descriptor;
        this.values = values;
        //
        this.detections = new Vector<Molecule>();
        this.sortedDetections = true;
    }
    
    public Molecule(MoleculeDescriptor descriptor, double [] values) {
        this.descriptor = descriptor;
        this.values = new Vector<Double>();
        for(int i = 0; i < values.length; i++) {
            this.values.add(values[i]);
        }
        //
        this.detections = new Vector<Molecule>();
        this.sortedDetections = true;
    }
    
    public Molecule(Params params) {
        assert(params.hasParam(Params.X) && params.hasParam(Params.Y));
        
        this.descriptor = new MoleculeDescriptor(params);
        this.values = new Vector<Double>();
        for(int i = 0; i < params.values.length; i++) {
            values.add(params.values[i]);
        }
        //
        this.detections = new Vector<Molecule>();
        this.sortedDetections = true;
    }
    
    public Molecule(Molecule mol) {
        this.descriptor = mol.descriptor;
        this.values = mol.values;
        this.detections = mol.detections;
        this.sortedDetections = mol.sortedDetections;
    }
    
    public void addParam(String name, Units units, double value) {
        insertParamAt(descriptor.getParamsCount(), name, units, value);
    }
    
    public void insertParamAt(int column, String name, Units units, double value) {
        if(hasParam(name)) {   // is the param already present in the table?
            setParam(name, value);  // then just set the value
        } else {                // if it's not, then add new column and set the value
            try {
                descriptor.insertParamAt(column, name, values.size(), units);
            } catch(Exception ex) {
                assert(false) : "This was supposed to never happen due to the `hasParam` check!";
            }
            values.add(value);  // values can be added at the last position since we implement extra indexing for this
        }
    }
    
    public boolean hasParam(String name) {
        return descriptor.hasParam(name);
    }

    public double getParamAt(int i) {
        return values.elementAt(i).doubleValue();
    }
    
    public void setParamAt(int i, double value) {
        if(i >= values.size()) {
            values.insertElementAt(value, i);
        } else {
            values.setElementAt(value, i);
        }
    }
    
    public double getParam(String param) {
        return values.elementAt(descriptor.getParamIndex(param)).doubleValue();
    }
    
    public void setParam(String param, double value) {
        setParamAt(descriptor.getParamIndex(param), value);
    }

    public String getParamNameAt(int i) {
        return descriptor.getParamNameAt(i);
    }
    
    public double getX() {
        return getParam(Params.LABEL_X);
    }
    
    public void setX(double value) {
        setParam(Params.LABEL_X, value);
    }
    
    public double getY() {
        return getParam(Params.LABEL_Y);
    }
    
    public void setY(double value) {
        setParam(Params.LABEL_Y, value);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0, im = descriptor.labels.size(); i < im; i++) {
            if (i != 0) {
                sb.append(", ");
            }
            sb.append(descriptor.labels.elementAt(i));
            sb.append("=");
            sb.append(values.elementAt(descriptor.indices.elementAt(i)));
        }
        sb.append("]");
        return sb.toString();
    }
    
    @Override
    public Molecule clone() {
        assert(true) : "Use clone(MoleculeDescriptor) instead!!!";
        throw new UnsupportedOperationException("Use `Molecule.clone(MoleculeDescriptor)` instead!!!");
    }
    
    /**
     * Clone the molecule.
     * 
     * Caller has to duplicate the descriptor if it is required!
     */
    public Molecule clone(MoleculeDescriptor descriptor) {
        Vector<Double> vals = new Vector<Double>();
        for(Double v : values) {
            vals.add(v.doubleValue());
        }
        Molecule mol = new Molecule(descriptor, vals);
        mol.sortedDetections = sortedDetections;
        mol.detections = new Vector<Molecule>(detections);
        return mol;
    }

    public static double[] extractParamToArray(List<Molecule> fits, int param) {
        double[] array = new double[fits.size()];
        for (int i = 0; i < fits.size(); i++) {
            array[i] = fits.get(i).getParamAt(param);
        }
        return array;
    }
    
    public static double[] extractParamToArray(List<Molecule> fits, String param) {
        double[] array = new double[fits.size()];
        for (int i = 0; i < fits.size(); i++) {
            array[i] = fits.get(i).getParam(param);
        }
        return array;
    }
    
    // ======================== [ MERGING ] ===================================
    
    public void updateParameters() {
        try {
            for(int i = 0, im = descriptor.getParamsCount(); i < im; i++) {
                MoleculeDescriptor.MergingOperations.merge(this, detections, descriptor.getParamNameAt(i));
            }
        } catch(Exception ex) {
            ex.printStackTrace();
            IJ.showMessage("Error!", ex.toString());
        }
    }

    public double dist2xy(Molecule mol) {
        return (sqr(mol.getX() - getX()) + sqr(mol.getY() - getY()));
    }

    public void addDetection(Molecule mol) {
        if(mol.detections.isEmpty()) {
            detections.add(mol);
        } else {    // if it is not empty, it already contains, at least, itself
            for(Molecule m : mol.detections) {
                detections.add(m);
            }
        }
        updateParameters();
        sortedDetections = false;
    }

    public Vector<Molecule> getDetections() {
        if(sortedDetections == false) {
            Collections.sort(detections);
            sortedDetections = true;
        }
        return detections;
    }

    public boolean isSingleMolecule() {
        return (detections.size() == 1);
    }

    @Override
    public int compareTo(Molecule mol) {
        // first by frame, then by id, but it should never happen,
        // since two molecules cannot be merged if they are in the same frame
        double frame = getParam(MoleculeDescriptor.LABEL_FRAME), molFrame = mol.getParam(MoleculeDescriptor.LABEL_FRAME);
        if(frame == molFrame) {
            return (int)(getParam(MoleculeDescriptor.LABEL_ID) - mol.getParam(MoleculeDescriptor.LABEL_ID));
        } else {
            return (int)(frame - molFrame);
        }
    }

}