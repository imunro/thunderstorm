package cz.cuni.lf1.lge.ThunderSTORM.util;

import static cz.cuni.lf1.lge.ThunderSTORM.estimators.PSF.Molecule.DetectionStatus.FALSE_NEGATIVE;
import static cz.cuni.lf1.lge.ThunderSTORM.estimators.PSF.Molecule.DetectionStatus.FALSE_POSITIVE;
import static cz.cuni.lf1.lge.ThunderSTORM.estimators.PSF.Molecule.DetectionStatus.TRUE_POSITIVE;
import static cz.cuni.lf1.lge.ThunderSTORM.estimators.PSF.MoleculeDescriptor.LABEL_DISTANCE_TO_GROUND_TRUTH;
import static cz.cuni.lf1.lge.ThunderSTORM.estimators.PSF.MoleculeDescriptor.LABEL_GROUND_TRUTH_ID;
import static cz.cuni.lf1.lge.ThunderSTORM.estimators.PSF.MoleculeDescriptor.LABEL_ID;
import static cz.cuni.lf1.lge.ThunderSTORM.util.MathProxy.sqrt;
import cz.cuni.lf1.lge.ThunderSTORM.estimators.PSF.Molecule;
import cz.cuni.lf1.lge.ThunderSTORM.estimators.PSF.MoleculeDescriptor.Units;
import cz.cuni.lf1.lge.ThunderSTORM.util.javaml.kdtree.KDTree;
import cz.cuni.lf1.lge.ThunderSTORM.util.javaml.kdtree.KeyDuplicateException;
import cz.cuni.lf1.lge.ThunderSTORM.util.javaml.kdtree.KeySizeException;
import java.util.List;
import java.util.Map;

public class MoleculeMatcher {

    public Units distUnits;
    public double dist2Thr;

    public MoleculeMatcher(double dist2Thr, Units distUnits) {
        this.dist2Thr = dist2Thr;
        this.distUnits = distUnits;
    }

    /**
     * The method matches detected molecules to the ground-truth data.
     * @param det [in] List of detected molecules.
     * @param gt [in] List of ground-truth molecules.
     * @param TP [out] List of true-positive pairs &lt;ground-truth,detection&gt; (the container must be allocated by caller).
     * @param FP [out] List of false-positive detections (the container must be allocated by caller).
     * @param FN [out] List of false-negative items from ground-truth (the container must be allocated by caller).
     */
    public void matchMolecules(List<Molecule> det, List<Molecule> gt, List<Pair<Molecule, Molecule>> TP, List<Molecule> FP, List<Molecule> FN) {
        if(det == null || gt == null || TP == null || FP == null || FN == null) {
            return;
        }
        //
        // Clean the data
        for(Molecule d : det) {
            if(d.getStatus() != Molecule.DetectionStatus.UNSPECIFIED) {
                if(d.neighbors != null) d.neighbors.clear();
                d.setStatus(Molecule.DetectionStatus.UNSPECIFIED);
                d.setParam(LABEL_GROUND_TRUTH_ID, Units.UNITLESS, 0);
                d.setParam(LABEL_DISTANCE_TO_GROUND_TRUTH, distUnits, Double.POSITIVE_INFINITY);
            }
        }
        for(Molecule g : gt) {
            if(g.getStatus() != Molecule.DetectionStatus.UNSPECIFIED) {
                if(g.neighbors != null) g.neighbors.clear();
                g.setStatus(Molecule.DetectionStatus.UNSPECIFIED);
            }
        }
        //
        // Initialize
        KDTree<Molecule> tree = new KDTree<Molecule>(3);
        try {
            for (Molecule mol : gt) {
                try {
                    tree.insert(new double[]{mol.getX(distUnits), mol.getY(distUnits), mol.getZ(distUnits)}, mol);
                }  catch(KeyDuplicateException ex) {
                    // this might theoreticaly happen if two molecules are located at the same exact spot; but it is very unlikely
                }
            }
            double dist = sqrt(dist2Thr);
            for (Molecule mol : det) {
                FP.add(mol);
                mol.addNeighbors(tree.range(
                        new double[] { mol.getX(distUnits) - dist, mol.getY(distUnits) - dist, mol.getZ(distUnits) - dist },
                        new double[] { mol.getX(distUnits) + dist, mol.getY(distUnits) + dist, mol.getZ(distUnits) + dist }), dist2Thr, distUnits);
            }
        } catch(KeySizeException ex) {
            // this will never happen since all the input is administered here
        }
        //
        // Perform the matching in the neighbourhood (given by dist2Thr) of each molecule
        Map<Molecule, Molecule> pairs = new StableMatching().match(det, gt);
        //
        // Set the results (TP, FP, FN)
        for (Molecule gtMol : gt) {
            Molecule detMol = pairs.get(gtMol);
            if(detMol != null) {
                gtMol.setStatus(TRUE_POSITIVE);
                detMol.setStatus(TRUE_POSITIVE);
                FP.remove(detMol);
                TP.add(new Pair<Molecule, Molecule>(gtMol, detMol));
                detMol.addParam(LABEL_GROUND_TRUTH_ID, Units.UNITLESS, gtMol.getParam(LABEL_ID));
                detMol.addParam(LABEL_DISTANCE_TO_GROUND_TRUTH, distUnits, gtMol.getDist(detMol, distUnits));
            } else {
                FN.add(gtMol);
                gtMol.setStatus(FALSE_NEGATIVE);
            }
        }
        for(Molecule detMol : FP) {
            detMol.setStatus(FALSE_POSITIVE);
            detMol.addParam(LABEL_GROUND_TRUTH_ID, Units.UNITLESS, 0);
            detMol.addParam(LABEL_DISTANCE_TO_GROUND_TRUTH, distUnits, Double.POSITIVE_INFINITY);
        }
    }

}
