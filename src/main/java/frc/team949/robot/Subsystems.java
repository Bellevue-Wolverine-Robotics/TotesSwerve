package frc.team949.robot;

import static frc.team949.robot.Subsystems.SubsystemConstants.*;

import frc.team949.robot.sensors.AprilTagsProcessor;
import frc.team949.robot.subsystems.DrivebaseSubsystem;
import frc.team949.robot.util.DrivebaseWrapper;

public class Subsystems {
	public static class SubsystemConstants {
		public static final boolean APRILTAGS_ENABLED = false;
		public static final boolean USE_APRILTAGS_CORRECTION = false;
		public static final boolean DRIVEBASE_ENABLED = true;
	}

	public final DrivebaseWrapper drivebaseWrapper;
	public final DrivebaseSubsystem drivebaseSubsystem;
	public final AprilTagsProcessor apriltagsProcessor;

	public Subsystems() {
		// initialize subsystems here (wow thats wild)
		if (DRIVEBASE_ENABLED) {
			drivebaseSubsystem = new DrivebaseSubsystem();
			drivebaseWrapper = new DrivebaseWrapper(drivebaseSubsystem.getSwerveDrive());
		} else {
			drivebaseSubsystem = null;
			drivebaseWrapper = new DrivebaseWrapper();
		}
		if (APRILTAGS_ENABLED) {
			apriltagsProcessor = new AprilTagsProcessor(drivebaseWrapper);
		} else {
			apriltagsProcessor = null;
		}
	}
}
