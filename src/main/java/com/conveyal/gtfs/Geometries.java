package com.conveyal.gtfs;

import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.impl.PackedCoordinateSequence;

/**
 * Make some bounding geometries for use in cropping.
 */
public class Geometries {

    public static final GeometryFactory geometryFactory = new GeometryFactory();

    /**
     * Taken from geofabrik.de country boundary KML, because our OSM PBF files use this same boundary.
     */
    public static Geometry getNetherlands() {
        CoordinateSequence netherlandsBoundaryCoordinates = new PackedCoordinateSequence.Double(new double[]{
                        5.920645, 50.748480,
                        5.914113, 50.747530,
                        5.911786, 50.752290,
                        5.900865, 50.748480,
                        5.888615, 50.753490,
                        5.885185, 50.766310,
                        5.864401, 50.760600,
                        5.849655, 50.750500,
                        5.842990, 50.761710,
                        5.831393, 50.755630,
                        5.806889, 50.753150,
                        5.792512, 50.766680,
                        5.782796, 50.764700,
                        5.773869, 50.780210,
                        5.749096, 50.768200,
                        5.740229, 50.754260,
                        5.720635, 50.760300,
                        5.699182, 50.752280,
                        5.681583, 50.754740,
                        5.680362, 50.763910,
                        5.698492, 50.782620,
                        5.688232, 50.795590,
                        5.696357, 50.805360,
                        5.652121, 50.817740,
                        5.635965, 50.846910,
                        5.642047, 50.873480,
                        5.676125, 50.883900,
                        5.693287, 50.899480,
                        5.698111, 50.913440,
                        5.721419, 50.911900,
                        5.725882, 50.927620,
                        5.738869, 50.937550,
                        5.744784, 50.949700,
                        5.756042, 50.953140,
                        5.746100, 50.958630,
                        5.725894, 50.952350,
                        5.716823, 50.961980,
                        5.735358, 50.980980,
                        5.748154, 50.984990,
                        5.764667, 51.000650,
                        5.764155, 51.013410,
                        5.773253, 51.022760,
                        5.757832, 51.028300,
                        5.755501, 51.035840,
                        5.767694, 51.049870,
                        5.772074, 51.064960,
                        5.797835, 51.062840,
                        5.793751, 51.072700,
                        5.801836, 51.079130,
                        5.793118, 51.086550,
                        5.793639, 51.093300,
                        5.806251, 51.099130,
                        5.823016, 51.095330,
                        5.829870, 51.098960,
                        5.828523, 51.103860,
                        5.811779, 51.105140,
                        5.804669, 51.113010,
                        5.819013, 51.129230,
                        5.839765, 51.134040,
                        5.847521, 51.144960,
                        5.834189, 51.151610,
                        5.829746, 51.160620,
                        5.824630, 51.164330,
                        5.816153, 51.156060,
                        5.805567, 51.159410,
                        5.777226, 51.148330,
                        5.771871, 51.153870,
                        5.774877, 51.160270,
                        5.767215, 51.163820,
                        5.772426, 51.177410,
                        5.766246, 51.180770,
                        5.745984, 51.186290,
                        5.740733, 51.181920,
                        5.709280, 51.177420,
                        5.688692, 51.182170,
                        5.657367, 51.181820,
                        5.647189, 51.192040,
                        5.648135, 51.196140,
                        5.559201, 51.219600,
                        5.551906, 51.244010,
                        5.553776, 51.264200,
                        5.527008, 51.279270,
                        5.513870, 51.292470,
                        5.485651, 51.296920,
                        5.465753, 51.281710,
                        5.444162, 51.279340,
                        5.417866, 51.259320,
                        5.349209, 51.272180,
                        5.337141, 51.260140,
                        5.295677, 51.258520,
                        5.263199, 51.263750,
                        5.236915, 51.258520,
                        5.222979, 51.268460,
                        5.238275, 51.303600,
                        5.200519, 51.319290,
                        5.162749, 51.307320,
                        5.131953, 51.313700,
                        5.128835, 51.345240,
                        5.068350, 51.392500,
                        5.100715, 51.431820,
                        5.077345, 51.468360,
                        5.044581, 51.468360,
                        5.035358, 51.477740,
                        5.036552, 51.484140,
                        5.012929, 51.469860,
                        5.013917, 51.454840,
                        5.006537, 51.441650,
                        4.921044, 51.390690,
                        4.911727, 51.392960,
                        4.913135, 51.398490,
                        4.905760, 51.405610,
                        4.888757, 51.412180,
                        4.876084, 51.412170,
                        4.867381, 51.406110,
                        4.839025, 51.411590,
                        4.789316, 51.405990,
                        4.769536, 51.412410,
                        4.763576, 51.430300,
                        4.766441, 51.433650,
                        4.787077, 51.435690,
                        4.824689, 51.426100,
                        4.820426, 51.449230,
                        4.833136, 51.460930,
                        4.837352, 51.478910,
                        4.819916, 51.480330,
                        4.814349, 51.491900,
                        4.782196, 51.496690,
                        4.778101, 51.501420,
                        4.754141, 51.497100,
                        4.748593, 51.487300,
                        4.731148, 51.481530,
                        4.718609, 51.466400,
                        4.706865, 51.464440,
                        4.694952, 51.449370,
                        4.670006, 51.442420,
                        4.671392, 51.423890,
                        4.641470, 51.419020,
                        4.574917, 51.429810,
                        4.534364, 51.420220,
                        4.526970, 51.450180,
                        4.544128, 51.472940,
                        4.537175, 51.479270,
                        4.476857, 51.475260,
                        4.465859, 51.468230,
                        4.443222, 51.465730,
                        4.393420, 51.449040,
                        4.401744, 51.434580,
                        4.397519, 51.425000,
                        4.387858, 51.419980,
                        4.393899, 51.410180,
                        4.434043, 51.376910,
                        4.432594, 51.361120,
                        4.422241, 51.362140,
                        4.384498, 51.351350,
                        4.340371, 51.354730,
                        4.332372, 51.374530,
                        4.222024, 51.371040,
                        4.244665, 51.352500,
                        4.167273, 51.290120,
                        4.062695, 51.241690,
                        4.040278, 51.238330,
                        4.014192, 51.241800,
                        3.987056, 51.230400,
                        3.980126, 51.222620,
                        3.966785, 51.221280,
                        3.959444, 51.212940,
                        3.935476, 51.208930,
                        3.928753, 51.215900,
                        3.921538, 51.214080,
                        3.919711, 51.204970,
                        3.886517, 51.197170,
                        3.874627, 51.207940,
                        3.887993, 51.219510,
                        3.859817, 51.207840,
                        3.837323, 51.210640,
                        3.824674, 51.205960,
                        3.789446, 51.211540,
                        3.785459, 51.226510,
                        3.790882, 51.254070,
                        3.775507, 51.260170,
                        3.768807, 51.256900,
                        3.753589, 51.266830,
                        3.693150, 51.273140,
                        3.657541, 51.287200,
                        3.640011, 51.285180,
                        3.589920, 51.301560,
                        3.586048, 51.298150,
                        3.590697, 51.291810,
                        3.582435, 51.283980,
                        3.562355, 51.292800,
                        3.556428, 51.286890,
                        3.545540, 51.288170,
                        3.538944, 51.280780,
                        3.519408, 51.284680,
                        3.528826, 51.243300,
                        3.448568, 51.238510,
                        3.426458, 51.241840,
                        3.420358, 51.253670,
                        3.406010, 51.254300,
                        3.373119, 51.275680,
                        3.377565, 51.285800,
                        3.363010, 51.298030,
                        3.370756, 51.302930,
                        3.355429, 51.315530,
                        3.381920, 51.335370,
                        3.380865, 51.339460,
                        3.371422, 51.347100,
                        3.371675, 51.358150,
                        3.350258, 51.376730,
                        3.306017, 51.431030,
                        3.078404, 51.551970,
                        2.992192, 51.633610,
                        4.362889, 53.305060,
                        5.530069, 54.017860,
                        6.346609, 53.728270,
                        6.414744, 53.697610,
                        6.594984, 53.595190,
                        6.821311, 53.496490,
                        6.858844, 53.471530,
                        6.891564, 53.468070,
                        6.917659, 53.459810,
                        6.934755, 53.439730,
                        6.954578, 53.395700,
                        6.995954, 53.350730,
                        6.997671, 53.334020,
                        7.006850, 53.329430,
                        7.061512, 53.327040,
                        7.111054, 53.334810,
                        7.189534, 53.334480,
                        7.220022, 53.214900,
                        7.220549, 53.197160,
                        7.212982, 53.189670,
                        7.230455, 53.180790,
                        7.205009, 53.173520,
                        7.194018, 53.160900,
                        7.192205, 53.146470,
                        7.182299, 53.135820,
                        7.188436, 53.122690,
                        7.205696, 53.114170,
                        7.202251, 53.081510,
                        7.215711, 53.012480,
                        7.220429, 53.006720,
                        7.211986, 52.998620,
                        7.183969, 52.939860,
                        7.090966, 52.849050,
                        7.095411, 52.836830,
                        7.074470, 52.809300,
                        7.057560, 52.641040,
                        7.039568, 52.629710,
                        6.975989, 52.643210,
                        6.953745, 52.636000,
                        6.918933, 52.636860,
                        6.912264, 52.644010,
                        6.872838, 52.650240,
                        6.821407, 52.644690,
                        6.784084, 52.649540,
                        6.743337, 52.642560,
                        6.717383, 52.627770,
                        6.730256, 52.615370,
                        6.722265, 52.590200,
                        6.768056, 52.566190,
                        6.769131, 52.559890,
                        6.752295, 52.556040,
                        6.727875, 52.559800,
                        6.716428, 52.545530,
                        6.687630, 52.549380,
                        6.708209, 52.521860,
                        6.700986, 52.488160,
                        6.753652, 52.466990,
                        6.774899, 52.462600,
                        6.854735, 52.462670,
                        6.856230, 52.453520,
                        6.941407, 52.438530,
                        6.959788, 52.446690,
                        6.975400, 52.467790,
                        6.988624, 52.472590,
                        6.996353, 52.467220,
                        7.013247, 52.431210,
                        7.024209, 52.425000,
                        7.037172, 52.405650,
                        7.060917, 52.401460,
                        7.075066, 52.373840,
                        7.074867, 52.350400,
                        7.059279, 52.336640,
                        7.050042, 52.322630,
                        7.050009, 52.313690,
                        7.029410, 52.290960,
                        7.031167, 52.275610,
                        7.044544, 52.258330,
                        7.068264, 52.242930,
                        7.063317, 52.232510,
                        7.026832, 52.222750,
                        6.989159, 52.223250,
                        6.953173, 52.178760,
                        6.910407, 52.173560,
                        6.907694, 52.167660,
                        6.884791, 52.155000,
                        6.875589, 52.128170,
                        6.856423, 52.117620,
                        6.763181, 52.115770,
                        6.758851, 52.095340,
                        6.748639, 52.091730,
                        6.754072, 52.085770,
                        6.750768, 52.080660,
                        6.736213, 52.071730,
                        6.699834, 52.067700,
                        6.690838, 52.053740,
                        6.690935, 52.042880,
                        6.714943, 52.042950,
                        6.753837, 52.031280,
                        6.814332, 51.997460,
                        6.828034, 51.996170,
                        6.835752, 51.973060,
                        6.830667, 51.962000,
                        6.801478, 51.956260,
                        6.799999, 51.941300,
                        6.790685, 51.927210,
                        6.771269, 51.913460,
                        6.722739, 51.893160,
                        6.693687, 51.911890,
                        6.681975, 51.914160,
                        6.634806, 51.898070,
                        6.586462, 51.891150,
                        6.561388, 51.879320,
                        6.544918, 51.881310,
                        6.505764, 51.865820,
                        6.501361, 51.859470,
                        6.472824, 51.850840,
                        6.449746, 51.862380,
                        6.431991, 51.856420,
                        6.425879, 51.862940,
                        6.392888, 51.869980,
                        6.390379, 51.864800,
                        6.411849, 51.854130,
                        6.405673, 51.843820,
                        6.411140, 51.835980,
                        6.407495, 51.825030,
                        6.380975, 51.831870,
                        6.366882, 51.830600,
                        6.360515, 51.833680,
                        6.358099, 51.843710,
                        6.346470, 51.847750,
                        6.305316, 51.846210,
                        6.295315, 51.865260,
                        6.279113, 51.871180,
                        6.262056, 51.865150,
                        6.209305, 51.866150,
                        6.181319, 51.880520,
                        6.179351, 51.887170,
                        6.185248, 51.889760,
                        6.156526, 51.902040,
                        6.116557, 51.895290,
                        6.138796, 51.888300,
                        6.147601, 51.871940,
                        6.169398, 51.863420,
                        6.167472, 51.837860,
                        6.100394, 51.846090,
                        6.064946, 51.861680,
                        6.057291, 51.850110,
                        6.036744, 51.839840,
                        6.028512, 51.841750,
                        5.994722, 51.827930,
                        5.963244, 51.833400,
                        5.950530, 51.824140,
                        5.960760, 51.818410,
                        5.957302, 51.812950,
                        5.981246, 51.799710,
                        5.978057, 51.788780,
                        5.992548, 51.785320,
                        5.987021, 51.775330,
                        5.995037, 51.770670,
                        5.991943, 51.763650,
                        5.956302, 51.748090,
                        5.956064, 51.742220,
                        5.994732, 51.741260,
                        6.046624, 51.719400,
                        6.043521, 51.710750,
                        6.029834, 51.708290,
                        6.034777, 51.692680,
                        6.033234, 51.679120,
                        6.037929, 51.675530,
                        6.119859, 51.658390,
                        6.120054, 51.649660,
                        6.110479, 51.640020,
                        6.095011, 51.607500,
                        6.123231, 51.595190,
                        6.132643, 51.583300,
                        6.158656, 51.569100,
                        6.178861, 51.540980,
                        6.201415, 51.529990,
                        6.214704, 51.514740,
                        6.215800, 51.492330,
                        6.226676, 51.468740,
                        6.209491, 51.403570,
                        6.229287, 51.401730,
                        6.218449, 51.386690,
                        6.229390, 51.360290,
                        6.194644, 51.338740,
                        6.195852, 51.332350,
                        6.172269, 51.330150,
                        6.156020, 51.305400,
                        6.131365, 51.283730,
                        6.126709, 51.272700,
                        6.077269, 51.241140,
                        6.088301, 51.220720,
                        6.071111, 51.218870,
                        6.076000, 51.184150,
                        6.083935, 51.174430,
                        6.105466, 51.175170,
                        6.165647, 51.197330,
                        6.182602, 51.188700,
                        6.183035, 51.184450,
                        6.147734, 51.172980,
                        6.177047, 51.160980,
                        6.177587, 51.156390,
                        6.164554, 51.145950,
                        6.126782, 51.141710,
                        6.093797, 51.131760,
                        6.088709, 51.122140,
                        6.061897, 51.113350,
                        6.038192, 51.094000,
                        5.999214, 51.081580,
                        5.973847, 51.059610,
                        5.971765, 51.045220,
                        5.958617, 51.031850,
                        5.936460, 51.032630,
                        5.913405, 51.063380,
                        5.893022, 51.050310,
                        5.869790, 51.048620,
                        5.880831, 51.038650,
                        5.878007, 51.028400,
                        5.881900, 51.019230,
                        5.897435, 51.013490,
                        5.898569, 51.006870,
                        5.908300, 51.003290,
                        5.906599, 50.986330,
                        5.899490, 50.977780,
                        5.955096, 50.991420,
                        5.967899, 50.982380,
                        5.981279, 50.986220,
                        6.027451, 50.986120,
                        6.029409, 50.979820,
                        6.012523, 50.957300,
                        6.019548, 50.953960,
                        6.020674, 50.937290,
                        6.051754, 50.932770,
                        6.058370, 50.924710,
                        6.097096, 50.921280,
                        6.080364, 50.898360,
                        6.079201, 50.890810,
                        6.089250, 50.881000,
                        6.091101, 50.872330,
                        6.079903, 50.858810,
                        6.076394, 50.844520,
                        6.055450, 50.848330,
                        6.053880, 50.853070,
                        6.021010, 50.843360,
                        6.019818, 50.833840,
                        6.028167, 50.828600,
                        6.027036, 50.811900,
                        6.004115, 50.798500,
                        5.985312, 50.807300,
                        5.978653, 50.801100,
                        6.029923, 50.776130,
                        6.021710, 50.762600,
                        6.023293, 50.752340,
                        5.982748, 50.749850,
                        5.958986, 50.759260,
                        5.920645, 50.748480}, 2, 0);
        Polygon boundary = geometryFactory.createPolygon(netherlandsBoundaryCoordinates);
        return boundary;
    }

    /**
     * The Dutch island of Texel. The Netherlands data set includes a crazy all-to-all representation of Texel's
     * on-demand transit service.
     */
    public static Geometry getTexel() {
        return geometryFactory.toGeometry(new Envelope(4.702663, 4.933548, 52.98069, 53.192047));
    }

    public static Geometry getNetherlandsWithoutTexel() {
        return getNetherlands().difference(getTexel());
    }

    public static Geometry getZuidHolland() {
        return geometryFactory.toGeometry(new Envelope(4.002074, 4.98848, 51.696796, 52.278661));
    }

}
