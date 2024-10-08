package frc.team949.robot.sensors;

import edu.wpi.first.apriltag.AprilTagFieldLayout;
import edu.wpi.first.apriltag.AprilTagFields;
import edu.wpi.first.math.VecBuilder;
import edu.wpi.first.math.Vector;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Pose3d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Rotation3d;
import edu.wpi.first.math.geometry.Transform3d;
import edu.wpi.first.math.numbers.N3;
import edu.wpi.first.math.util.Units;
import edu.wpi.first.networktables.NetworkTableEvent;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.wpilibj.shuffleboard.Shuffleboard;
import edu.wpi.first.wpilibj.shuffleboard.ShuffleboardTab;
import edu.wpi.first.wpilibj.smartdashboard.FieldObject2d;
import frc.team949.robot.Hardware;
import frc.team949.robot.util.DrivebaseWrapper;
import frc.team949.robot.util.SendablePose3d;
import java.util.EnumSet;
import java.util.Optional;
import org.photonvision.EstimatedRobotPose;
import org.photonvision.PhotonCamera;
import org.photonvision.PhotonPoseEstimator;
import org.photonvision.PhotonPoseEstimator.PoseStrategy;
import org.photonvision.targeting.PhotonPipelineResult;

/**
 * All 3D poses and transforms use the NWU (North-West-Up) coordinate system, where +X is
 * north/forward, +Y is west/left, and +Z is up. On the field, this is based on the blue driver
 * station. (+X is forward from blue driver station, +Y is left, and +Z is up. Additionally, the
 * origin is the right corner of the blue driver station- All coordinates should be non-negative.)
 *
 * <p>Rotations follow the right-hand rule- If the thumb of a right hand is pointed in a positive
 * axis, the fingers curl in the direction of positive rotation. Alternatively, the rotation is CCW+
 * looking into the positive axis.
 *
 * <p>2D field poses are just the projection of the 3D pose onto the XY plane.
 */
public class AprilTagsProcessor {
	public static final Transform3d ROBOT_TO_CAM =
			new Transform3d(
					Units.inchesToMeters(27.0 / 2.0 - 0.94996),
					0,
					Units.inchesToMeters(8.12331),
					new Rotation3d(Units.degreesToRadians(90), Units.degreesToRadians(-30), 0));
	public static final Transform3d ROBOT_TO_CAM_2 =
			new Transform3d(
					Units.inchesToMeters(27.0 / 2.0 - 0.94996),
					0,
					Units.inchesToMeters(8.12331),
					new Rotation3d(Units.degreesToRadians(90), Units.degreesToRadians(-30), 0));

	// TODO Measure these
	private static final Vector<N3> STANDARD_DEVS =
			VecBuilder.fill(0.2, 0.2, Units.degreesToRadians(30));

	private static final double MAX_POSE_AMBIGUITY = 0.1;

	// Radians
	private static final double ROBOT_TO_TARGET_ROLL_TOLERANCE = 0.1;
	private static final double ROBOT_TO_TARGET_PITCH_TOLERANCE = 0.1;

	private static boolean hasValidRotation(Transform3d camToTarget) {
		// Intrinsic robot to cam + cam to target = extrinsic cam to target + robot to cam
		var robotToTargetRotation = camToTarget.getRotation().plus(ROBOT_TO_CAM.getRotation());
		return (Math.abs(robotToTargetRotation.getX()) < ROBOT_TO_TARGET_ROLL_TOLERANCE)
				&& (Math.abs(robotToTargetRotation.getY()) < ROBOT_TO_TARGET_PITCH_TOLERANCE);
	}

	private static boolean resultIsValid(PhotonPipelineResult result) {
		for (var target : result.targets) {
			if (target.getPoseAmbiguity() > MAX_POSE_AMBIGUITY) {
				return false;
			}
			if (!hasValidRotation(target.getBestCameraToTarget())) {
				return false;
			}
		}
		return true;
	}

	private final PhotonCamera photonCamera;
	private final PhotonCamera photonCamera2;
	private final PhotonPoseEstimator photonPoseEstimator;
	private final PhotonPoseEstimator photonPoseEstimator2;
	private final DrivebaseWrapper aprilTagsHelper;
	private final FieldObject2d rawVisionFieldObject;

	// These are always set with every pipeline result
	private double lastRawTimestampSeconds = 0;
	private PhotonPipelineResult latestResult = null;
	private boolean latestResultIsValid = false;

	private PhotonPipelineResult latestResult2 = null;
	private boolean latestResultIsValid2 = false;

	// This is set for every non-filtered pipeline result
	private Optional<EstimatedRobotPose> latestPose = Optional.empty();

	// These are only set when there's a valid pose
	private double lastValidTimestampSeconds = 0;
	private Pose2d lastFieldPose = new Pose2d(-1, -1, new Rotation2d());

	private static final AprilTagFieldLayout fieldLayout =
			AprilTagFields.k2024Crescendo.loadAprilTagLayoutField();

	public AprilTagsProcessor(DrivebaseWrapper aprilTagsHelper) {
		this.aprilTagsHelper = aprilTagsHelper;
		rawVisionFieldObject = aprilTagsHelper.getField().getObject("RawVision");
		var networkTables = NetworkTableInstance.getDefault();
		// if (Robot.isSimulation()) {
		// 	networkTables.stopServer();
		// 	networkTables.setServer(Hardware.PHOTON_IP);
		// 	networkTables.startClient4("Photonvision");
		// }

		photonCamera = new PhotonCamera(Hardware.PHOTON_CAM);
		photonPoseEstimator =
				new PhotonPoseEstimator(
						fieldLayout, PoseStrategy.MULTI_TAG_PNP_ON_COPROCESSOR, photonCamera, ROBOT_TO_CAM);
		photonCamera2 = new PhotonCamera(Hardware.PHOTON_CAM_2);
		photonPoseEstimator2 =
				new PhotonPoseEstimator(
						fieldLayout, PoseStrategy.MULTI_TAG_PNP_ON_COPROCESSOR, photonCamera2, ROBOT_TO_CAM_2);

		photonPoseEstimator.setLastPose(aprilTagsHelper.getEstimatedPosition());
		photonPoseEstimator2.setLastPose(aprilTagsHelper.getEstimatedPosition());
		photonPoseEstimator.setMultiTagFallbackStrategy(PoseStrategy.CLOSEST_TO_LAST_POSE);
		photonPoseEstimator2.setMultiTagFallbackStrategy(PoseStrategy.CLOSEST_TO_LAST_POSE);

		networkTables.addListener(
				networkTables
						.getTable("photonvision")
						.getSubTable(Hardware.PHOTON_CAM)
						.getEntry("rawBytes"),
				EnumSet.of(NetworkTableEvent.Kind.kValueAll),
				event -> update());
		networkTables.addListener(
				networkTables
						.getTable("photonvision")
						.getSubTable(Hardware.PHOTON_CAM_2)
						.getEntry("rawBytes"),
				EnumSet.of(NetworkTableEvent.Kind.kValueAll),
				event -> update());

		ShuffleboardTab shuffleboardTab = Shuffleboard.getTab("AprilTags");
		shuffleboardTab
				.addDouble("Last raw timestamp", this::getLastRawTimestampSeconds)
				.withPosition(0, 0)
				.withSize(1, 1);
		shuffleboardTab
				.addInteger("Num targets", this::getNumTargets)
				.withPosition(0, 1)
				.withSize(1, 1);
		shuffleboardTab
				.addDouble("Last timestamp", this::getLastValidTimestampSeconds)
				.withPosition(1, 0)
				.withSize(1, 1);
		shuffleboardTab
				.addBoolean("Has valid targets", this::hasTargets)
				.withPosition(1, 1)
				.withSize(1, 1);
		shuffleboardTab
				.add("3d pose on field", new SendablePose3d(this::getRobotPose))
				.withPosition(2, 0)
				.withSize(2, 2);
	}

	public void update() {
		latestResult = photonCamera.getLatestResult();
		latestResultIsValid = resultIsValid(latestResult);

		latestResult2 = photonCamera2.getLatestResult();
		latestResultIsValid2 = resultIsValid(latestResult2);

		if (latestResultIsValid || latestResultIsValid2) {
			if (latestResultIsValid) {
				latestPose = photonPoseEstimator.update(latestResult);
				lastRawTimestampSeconds = latestResult.getTimestampSeconds();
			} else {
				latestPose = photonPoseEstimator2.update(latestResult2);
				lastRawTimestampSeconds = latestResult2.getTimestampSeconds();
			}
			if (latestPose.isPresent()) {
				lastValidTimestampSeconds = latestPose.get().timestampSeconds;
				lastFieldPose = latestPose.get().estimatedPose.toPose2d();
				rawVisionFieldObject.setPose(lastFieldPose);
				aprilTagsHelper.addVisionMeasurement(
						lastFieldPose, lastValidTimestampSeconds, STANDARD_DEVS);
				var estimatedPose = aprilTagsHelper.getEstimatedPosition();
				aprilTagsHelper.getField().setRobotPose(estimatedPose);
				photonPoseEstimator.setLastPose(estimatedPose);
			}
		}
	}

	/**
	 * Returns the timestamp of the last result we got (regardless of whether it has any valid
	 * targets)
	 *
	 * @return The timestamp of the last result we got in seconds since FPGA startup.
	 */
	public double getLastRawTimestampSeconds() {
		return lastRawTimestampSeconds;
	}

	public int getNumTargets() {
		return latestResult == null ? -1 : latestResult.getTargets().size();
	}

	public boolean hasTargets() {
		return latestPose.isPresent();
	}

	/**
	 * Calculates the robot pose using the best target. Returns null if there is no known robot pose.
	 *
	 * @return The calculated robot pose in meters.
	 */
	public Pose3d getRobotPose() {
		if (latestPose.isPresent()) {
			return latestPose.get().estimatedPose;
		}
		return null;
	}

	/**
	 * Returns the last time we saw an AprilTag.
	 *
	 * @return The time we last saw an AprilTag in seconds since FPGA startup.
	 */
	public double getLastValidTimestampSeconds() {
		return lastValidTimestampSeconds;
	}
}
