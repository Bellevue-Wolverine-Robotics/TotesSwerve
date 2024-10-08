package frc.team949.robot;

import com.ctre.phoenix6.SignalLogger;
import edu.wpi.first.wpilibj.DataLogManager;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.DriverStation.Alliance;
import edu.wpi.first.wpilibj.PowerDistribution;
import edu.wpi.first.wpilibj.PowerDistribution.ModuleType;
import edu.wpi.first.wpilibj.RobotBase;
import edu.wpi.first.wpilibj.RobotController;
import edu.wpi.first.wpilibj.TimedRobot;
import edu.wpi.first.wpilibj.livewindow.LiveWindow;
import edu.wpi.first.wpilibj.shuffleboard.Shuffleboard;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.CommandScheduler;
import edu.wpi.first.wpilibj2.command.InstantCommand;
import edu.wpi.first.wpilibj2.command.WaitCommand;
import frc.team949.robot.util.MACAddress;

public class Robot extends TimedRobot {
	/** Singleton Stuff */
	private static Robot instance = null;

	public enum RobotType {
		COMPETITION,
		CRANE,
		BONK;
	}

	public static Robot getInstance() {
		if (instance == null) instance = new Robot();
		return instance;
	}

	// increases logging
	private static final boolean debugMode = false;
	// Really dangerous to keep this enabled as it disables all other controls, use with caution
	private static final boolean sysIdMode = false;

	// Turns on an off auto logic
	private static final boolean autoEnabled = true;

	private final RobotType robotType;
	private final PowerDistribution PDP;
	public Controls controls;
	public Subsystems subsystems;

	protected Robot(RobotType type) {
		// non public for singleton. Protected so test class can subclass
		instance = this;
		PDP = new PowerDistribution(Hardware.PDP_ID, ModuleType.kRev);
		robotType = type;
	}

	protected Robot() {
		this(getTypeFromAddress());
	}

	public static final MACAddress COMPETITION_ADDRESS = MACAddress.of(0x38, 0xd9, 0x9e);
	public static final MACAddress BONK_ADDRESS = MACAddress.of(0x33, 0x9D, 0xE7);
	public static final MACAddress CRANE_ADDRESS = MACAddress.of(0x22, 0xB0, 0x92);

	private static RobotType getTypeFromAddress() {
		if (CRANE_ADDRESS.exists()) return RobotType.CRANE;
		if (BONK_ADDRESS.exists()) return RobotType.BONK;
		if (!COMPETITION_ADDRESS.exists())
			DriverStation.reportWarning(
					"Code running on unknown MAC Address! Running competition code anyways", false);
		return RobotType.COMPETITION;
	}

	@Override
	public void robotInit() {
		LiveWindow.disableAllTelemetry();
		LiveWindow.enableTelemetry(PDP);

		subsystems = new Subsystems();
		controls = new Controls(subsystems);

		// TODO: might be a duplicate, keep until after comp

		SmartDashboard.putString("current bot", getTypeFromAddress().toString());

		Shuffleboard.startRecording();

		if (RobotBase.isReal()) {
			DataLogManager.start();
			DriverStation.startDataLog(DataLogManager.getLog(), true);
		}

		CommandScheduler.getInstance()
				.onCommandInitialize(
						command -> System.out.println("Command initialized: " + command.getName()));
		CommandScheduler.getInstance()
				.onCommandInterrupt(
						command -> System.out.println("Command interrupted: " + command.getName()));
		CommandScheduler.getInstance()
				.onCommandFinish(command -> System.out.println("Command finished: " + command.getName()));

		SmartDashboard.putData(CommandScheduler.getInstance());

		DriverStation.silenceJoystickConnectionWarning(true);

		RobotController.setBrownoutVoltage(5.75);
	}

	@Override
	public void testInit() {}

	@Override
	public void robotPeriodic() {
		CommandScheduler.getInstance().run();
	}

	@Override
	public void autonomousInit() {
		Shuffleboard.startRecording();

		// Checks if FMS is attatched and enables joystick warning if true
		DriverStation.silenceJoystickConnectionWarning(!DriverStation.isFMSAttached());
		// System.out.println(AutoLogic.getSelected() != null);
		if (autoEnabled) {

			// nullcommand.schedule();

		}
	}

	@Override
	public void teleopInit() {
		Shuffleboard.startRecording();
		SignalLogger.start();
		CommandScheduler.getInstance().cancelAll();
	}

	@Override
	public void autonomousExit() {
		CommandScheduler.getInstance().cancelAll();
	}

	@Override
	public void teleopExit() {
		CommandScheduler.getInstance().cancelAll();
		SignalLogger.stop();
	}

	@Override
	public void disabledInit() {
		Shuffleboard.stopRecording();
		Command coastCommand =
				new WaitCommand(5)
						.andThen(
								new InstantCommand(
										() -> {
											if (DriverStation.isDisabled())
												subsystems.drivebaseSubsystem.setMotorBrake(false);
										}))
						.ignoringDisable(true)
						.withName("drivebaseCoast");
		coastCommand.schedule();
	}

	@Override
	public void disabledExit() {
		subsystems.drivebaseSubsystem.setMotorBrake(true);
	}

	public RobotType getRobotType() {
		return robotType;
	}

	public boolean isCompetition() {
		return getRobotType() == RobotType.COMPETITION;
	}

	public static boolean isDebugMode() {
		return debugMode;
	}

	public static boolean isSysIdMode() {
		return sysIdMode;
	}

	public static boolean isBlue() {
		return DriverStation.getAlliance().orElse(Alliance.Red) == Alliance.Blue;
	}

	public static boolean isRed() {
		return DriverStation.getAlliance().orElse(Alliance.Blue) == Alliance.Red;
	}
}
