package frc.team949.robot;

import static frc.team949.robot.Controls.ControlConstants.CODRIVER_CONTROLLER_PORT;
import static frc.team949.robot.Controls.ControlConstants.CONTROLLER_PORT;
import static frc.team949.robot.Controls.ControlConstants.JOYSTICK_DRIVE_PORT;
import static frc.team949.robot.Controls.ControlConstants.JOYSTICK_MODE;
import static frc.team949.robot.Subsystems.SubsystemConstants.DRIVEBASE_ENABLED;

import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.GenericHID.RumbleType;
import edu.wpi.first.wpilibj2.command.CommandScheduler;
import edu.wpi.first.wpilibj2.command.InstantCommand;
import edu.wpi.first.wpilibj2.command.button.CommandJoystick;
import edu.wpi.first.wpilibj2.command.button.CommandXboxController;
import edu.wpi.first.wpilibj2.command.sysid.SysIdRoutine.Direction;

public class Controls {
	public static class ControlConstants {
		public static final boolean JOYSTICK_MODE = true;

		public static final int CONTROLLER_PORT = 0;
		public static final int CODRIVER_CONTROLLER_PORT = 1;

		public static final int JOYSTICK_DRIVE_PORT = 0;

		public static final double RUMBLE_VIBRATION = 0.3;
		public static final double RUMBLE_OFF = 0;
	}

	private final CommandXboxController driveController;
	private final CommandXboxController codriveController;

	private final CommandJoystick joystickDriveController;
	// leaving this code in in case we need to test outside of auto
	// private final Trigger getWithinDistanceTrigger;

	private final Subsystems s;

	public Controls(Subsystems s) {
		if (JOYSTICK_MODE) {
			joystickDriveController = new CommandJoystick(JOYSTICK_DRIVE_PORT);
			driveController = null;
			codriveController = null;
		} else {
			joystickDriveController = null;
			driveController = new CommandXboxController(CONTROLLER_PORT);
			codriveController = new CommandXboxController(CODRIVER_CONTROLLER_PORT);
		}

		// not sure what drive team wants (or if the trigger is even needed since we are only using the
		// command in auto)
		// getWithinDistanceTrigger = driveController.start();
		this.s = s;

		// intake controls (confirmed with driveteam)

		if (Robot.isSysIdMode()) {
			bindSysIdControls();
			return;
		}
		if (DRIVEBASE_ENABLED) {
			bindDrivebaseControls();
		}
	}
	// LED

	// drivebase
	private void bindDrivebaseControls() {

		if (JOYSTICK_MODE) {
			CommandScheduler.getInstance()
					.setDefaultCommand(
							s.drivebaseSubsystem,
							s.drivebaseSubsystem.driveJoystick(
									joystickDriveController::getY,
									joystickDriveController::getX,
									() -> Rotation2d.fromRotations(joystickDriveController.getZ()),
									joystickDriveController.button(0)));
		} else {
			CommandScheduler.getInstance()
					.setDefaultCommand(
							s.drivebaseSubsystem,
							s.drivebaseSubsystem.driveJoystick(
									driveController::getLeftY,
									driveController::getLeftX,
									() -> Rotation2d.fromRotations(driveController.getRightX()),
									driveController.rightTrigger()));
			driveController.rightStick().onTrue(new InstantCommand(s.drivebaseSubsystem::toggleXWheels));
		}
	}

	// intake controls
	private void bindSysIdControls() {

		driveController.x().whileTrue(s.drivebaseSubsystem.driveSysIdQuasistatic(Direction.kForward));
		driveController.y().whileTrue(s.drivebaseSubsystem.driveSysIdQuasistatic(Direction.kReverse));
		driveController.a().whileTrue(s.drivebaseSubsystem.driveSysIdDynamic(Direction.kForward));
		driveController.b().whileTrue(s.drivebaseSubsystem.driveSysIdDynamic(Direction.kReverse));
		driveController.back().whileTrue(s.drivebaseSubsystem.debugDriveFullPower());
	}

	public void vibrateDriveController(double vibration) {
		if (!DriverStation.isAutonomous()) {
			driveController.getHID().setRumble(RumbleType.kBothRumble, vibration);
		}
	}

	public void vibrateCoDriveController(double vibration) {
		if (!DriverStation.isAutonomous()) {
			codriveController.getHID().setRumble(RumbleType.kBothRumble, vibration);
		}
	}
}
