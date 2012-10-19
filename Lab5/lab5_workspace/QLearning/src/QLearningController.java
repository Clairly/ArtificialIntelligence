import java.awt.Toolkit;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Random;

/* TODO: 
 * -Define state and reward functions (StateAndReward.java) suitable for your problem 
 * -Define actions
 * -Implement missing parts of Q-learning
 * -Tune state and reward function, and parameters below if the result is not satisfactory */

public class QLearningController extends Controller {

	/* These are the agents senses (inputs) */
	DoubleFeature x; /* Positions */
	DoubleFeature y;
	DoubleFeature vx; /* Velocities */
	DoubleFeature vy;
	DoubleFeature angle; /* Angle */

	/* These are the agents actuators (outputs) */
	RocketEngine leftEngine;
	RocketEngine middleEngine;
	RocketEngine rightEngine;
	boolean textToggle=true;

	final static int NUM_ACTIONS = 7; /*
	 * The takeAction function must be changed
	 * if this is modified
	 */

	/* Keep track of the previous state and action */
	String previous_state = null;
	double previous_vx = 0;
	double previous_vy = 0;
	double previous_angle = 0;
	int previous_action = 0;

	private static final int NO_ENGINE = 0;
	private static final int LEFT_ENGINE = 1;
	private static final int RIGHT_ENGINE = 2;
	private static final int MIDDLE_ENGINE = 3;
	private static final int LEFT_AND_MIDDLE_ENGINE = 4;
	private static final int RIGHT_AND_MIDDLE_ENGINE = 5;
	private static final int ALL_ENGINES = 6;

	/* The tables used by Q-learning */
	Hashtable<String, Double> Qtable = new Hashtable<String, Double>(); /*
	 * Contains
	 * the
	 * Q-
	 * values
	 * - the
	 * state
	 * -
	 * action
	 * utilities
	 */
	Hashtable<String, Integer> Ntable = new Hashtable<String, Integer>(); /*
	 * Keeps
	 * track
	 * of
	 * how
	 * many
	 * times
	 * each
	 * state
	 * -
	 * action
	 * combination
	 * has
	 * been
	 * used
	 */

	/*
	 * PARAMETERS OF THE LEARNING ALGORITHM - THESE MAY BE TUNED BUT THE DEFAULT
	 * VALUES OFTEN WORK REASONABLY WELL
	 */
	static final double GAMMA_DISCOUNT_FACTOR = 0.95; /*
	 * Must be < 1, small values
	 * make it very greedy
	 */
	static final double LEARNING_RATE_CONSTANT = 10; /*
	 * See alpha(), lower values
	 * are good for quick
	 * results in large and
	 * deterministic state
	 * spaces
	 */
	double explore_chance = 0.5; /*
	 * The exploration chance during the exploration
	 * phase
	 */
	final static int REPEAT_ACTION_MAX = 30; /*
	 * Repeat selected action at most
	 * this many times trying reach a
	 * new state, without a max it could
	 * loop forever if the action cannot
	 * lead to a new state
	 */

	/* Some internal counters */
	int iteration = 0; /* Keeps track of how many iterations the agent has run */
	int action_counter = 0; /*
	 * Keeps track of how many times we have repeated
	 * the current action
	 */
	int print_counter = 0; /* Makes printouts less spammy */

	/* These are just internal helper variables, you can ignore these */
	boolean paused = false;
	boolean explore = true; /* Will always do exploration by default */

	DecimalFormat df = (DecimalFormat) NumberFormat
			.getNumberInstance(Locale.US);
	public SpringObject object;
	ComposedSpringObject cso;
	long lastPressedExplore = 0;
	long lastPressed1 = 0;
	long lastPressed2 = 0;

	public void init() {
		cso = (ComposedSpringObject) object;
		x = (DoubleFeature) cso.getObjectById("x");
		y = (DoubleFeature) cso.getObjectById("y");
		vx = (DoubleFeature) cso.getObjectById("vx");
		vy = (DoubleFeature) cso.getObjectById("vy");
		angle = (DoubleFeature) cso.getObjectById("angle");

		previous_vy = vy.getValue();
		previous_vx = vx.getValue();
		previous_angle = angle.getValue();

		leftEngine = (RocketEngine) cso.getObjectById("rocket_engine_left");
		rightEngine = (RocketEngine) cso.getObjectById("rocket_engine_right");
		middleEngine = (RocketEngine) cso.getObjectById("rocket_engine_middle");
	}

	/* Turn off all rockets */
	void resetRockets() {
		leftEngine.setBursting(false);
		rightEngine.setBursting(false);
		middleEngine.setBursting(false);
	}

	/* Performs the chosen action */
	void performAction(int action) {
		resetRockets();
		switch (action) {
		case NO_ENGINE:
			break;
		case LEFT_ENGINE:
			leftEngine.setBursting(true);
			break;
		case RIGHT_ENGINE:
			rightEngine.setBursting(true);
			break;
		case MIDDLE_ENGINE:
			middleEngine.setBursting(true);
			break;
		case LEFT_AND_MIDDLE_ENGINE:
			leftEngine.setBursting(true);
			middleEngine.setBursting(true);
			break;
		case RIGHT_AND_MIDDLE_ENGINE:
			rightEngine.setBursting(true);
			middleEngine.setBursting(true);
			break;
		case ALL_ENGINES:
			rightEngine.setBursting(true);
			leftEngine.setBursting(true);
			middleEngine.setBursting(true);
			break;
		}
	}

	/* Main decision loop. Called every iteration by the simulator */
	public void tick(int currentTime) {
		// System.out.println("(" + vx.getValue() + ", " + vy.getValue() + ")");
		iteration++;
		if (iteration%500000==0){
			Toolkit.getDefaultToolkit().beep();
			Toolkit.getDefaultToolkit().beep();
			Toolkit.getDefaultToolkit().beep();
			System.out.println("## "+iteration + " ##");
		}

		// System.out.println(angle.getValue());
		if (!paused) {
			String new_state = "";
			// new_state += StateAndReward.getStateAngle(angle.getValue(),
			// vx.getValue(), vy.getValue());
			new_state += StateAndReward.getStateHover(angle.getValue(),
					vx.getValue(), vy.getValue());
			/*
			 * Repeat the chosen action for a while, hoping to reach a new
			 * state. This is a trick to speed up learning on this problem.
			 */
			action_counter++;
			if (new_state.equals(previous_state)
					&& action_counter < REPEAT_ACTION_MAX) {
				return;
			}
			double previous_reward = StateAndReward.getRewardHover(
					previous_angle, previous_vx, previous_vy);
			action_counter = 0;

			/* The agent is in a new state, do learning and action selection */
			if (previous_state != null) {
				/* Create state-action key */
				String prev_stateaction = previous_state + previous_action;

				/* Increment state-action counter */
				if (Ntable.get(prev_stateaction) == null) {
					Ntable.put(prev_stateaction, 0);
				}
				Ntable.put(prev_stateaction, Ntable.get(prev_stateaction) + 1);

				/* Update Q value */
				if (Qtable.get(prev_stateaction) == null) {
					Qtable.put(prev_stateaction, 0.0);
				}

				/* TODO: IMPLEMENT Q-UPDATE HERE! */

				/* See top for constants and below for helper functions */
				
				int action = selectAction(new_state); 
				if (Ntable.get(new_state + action) == null) {
					// System.out.println("New Value!");
					Ntable.put(new_state + action, 0);
				}
				if (Qtable.get(new_state + action) == null) {
					Qtable.put(new_state + action, 0d);
				}
				if (Ntable.get(previous_state) == null) {
					// System.out.println("New Value!");
					Ntable.put(previous_state, 0);
				}
				if (Qtable.get(previous_state) == null) {
					Qtable.put(previous_state, 0d);
				}
				double oldValue = Qtable.get(previous_state);
				double alpha = alpha(Ntable.get(previous_state));
				/*
				 * Make sure you understand
				 * how it selects an action
				 */
				performAction(action);
				// System.out.println(new_state);
				double reward = StateAndReward.getRewardHover(angle.getValue(), vx.getValue(), vy.getValue());
				double maxFutureValue = 0;
				for(int i=0; i < NUM_ACTIONS; ++i){
					if(Qtable.get(new_state+i) == null) {
						continue;
					}
					maxFutureValue = Math.max(maxFutureValue, Qtable.get(new_state+i));
				}
				Qtable.put(new_state+action, oldValue + alpha*(reward + 0.95*maxFutureValue - oldValue));
				/*
				if (explore) {
					if (Ntable.get(new_state + action) == null) {
						// System.out.println("New Value!");
						Ntable.put(new_state + action, 0);
					}
					if (Qtable.get(new_state + action) == null) {
						Qtable.put(new_state + action, 0d);
					}
					int numOfExplorations = Ntable.get(new_state + action);
					// System.out.println("numOfExplorations="+numOfExplorations);
					Qtable.put(new_state + action,
							Qtable.get(new_state + action)
							* 0.95
							//									+ StateAndReward.getRewardAngle(
							//											angle.getValue(), vx.getValue(),
							//											vy.getValue())
							+ StateAndReward.getRewardHover(
									angle.getValue(), vx.getValue(),
									vy.getValue()));
					// System.out.println(Qtable.size()+", "+Qtable.toString());
					// System.out.println(Ntable.size()+ ", "+
					// Ntable.toString());
					Ntable.put(new_state + action, numOfExplorations + 1);
				}

				/* Only print every 10th line to reduce spam */
				print_counter++;


				if (print_counter % 10 == 0) {

					if (textToggle){
						System.out.println("ITERATION: " + iteration
								+ " SENSORS: a=" + df.format(angle.getValue())
								+ " vx=" + df.format(vx.getValue()) + " vy="
								+ df.format(vy.getValue()) + " P_STATE: "
								+ previous_state + " P_ACTION: " + previous_action
								+ " P_REWARD: " + df.format(previous_reward)
								+ " P_QVAL: "
								+ df.format(Qtable.get(prev_stateaction))
								+ " Tested: " + Ntable.get(prev_stateaction)
								+ " times.");
					}
				}

				previous_vy = vy.getValue();
				previous_vx = vx.getValue();
				previous_angle = angle.getValue();
				previous_action = action;
			}
			previous_state = new_state;
		}

	}

	/*
	 * Computes the learning rate parameter alpha based on the number of times
	 * the state-action combination has been tested
	 */
	public double alpha(int num_tested) {
		/*
		 * Lower learning rate constants means that alpha will become small
		 * faster and therefore make the agent behavior converge to to a
		 * solution faster, but if the state space is not properly explored at
		 * that point the resulting behavior may be poor. If your state-space is
		 * really huge you may need to increase it.
		 */
		double alpha = (LEARNING_RATE_CONSTANT / (LEARNING_RATE_CONSTANT + num_tested));
		return alpha;
	}

	/* Finds the highest Qvalue of any action in the given state */
	public double getMaxActionQValue(String state) {
		double maxQval = Double.NEGATIVE_INFINITY;

		for (int action = 0; action < NUM_ACTIONS; action++) {
			Double Qval = Qtable.get(state + action);
			if (Qval != null && Qval > maxQval) {
				maxQval = Qval;
			}
		}

		if (maxQval == Double.NEGATIVE_INFINITY) {
			/* Assign 0 as that corresponds to initializing the Qtable to 0. */
			maxQval = 0;
		}
		return maxQval;
	}

	/*
	 * Selects an action in a state based on the registered Q-values and the
	 * exploration chance
	 */
	public int selectAction(String state) {
		Random rand = new Random();

		int action = 0;
		/* May do exploratory move if in exploration mode */
		if (explore && Math.abs(rand.nextDouble()) < explore_chance) {
			/* Taking random exploration action! */
			action = Math.abs(rand.nextInt()) % NUM_ACTIONS;
			return action;
		}

		/* Find action with highest Q-val (utility) in given state */
		double maxQval = Double.MIN_VALUE;
		for (int i = 0; i < NUM_ACTIONS; i++) {
			String test_pair = state + i; /*
			 * Generate a state-action pair for all
			 * actions
			 */
			double Qval = 0;
			if (Qtable.get(test_pair) != null) {
				Qval = Qtable.get(test_pair);
			}
			if (Qval > maxQval) {
				maxQval = Qval;
				action = i;
			}
		}
		return action;
	}

	/*
	 * The 'E' key will toggle the agents exploration mode. Turn this off to
	 * test its behavior
	 */
	public void toggleExplore() {
		/* Make sure we don't toggle it multiple times */
		if (System.currentTimeMillis() - lastPressedExplore < 1000) {
			return;
		}
		Toolkit.getDefaultToolkit().beep();
		if (explore) {
			System.out.println("Turning OFF exploration!");
			explore = false;
		} else {
			System.out.println("Turning ON exploration!");
			explore = true;
		}
		lastPressedExplore = System.currentTimeMillis();
	}

	/* Keys 1 and 2 can be customized for whatever purpose if you want to */
	public void toggleCustom1() {
		if (System.currentTimeMillis() - lastPressed1 < 1000) {
			return;
		}
		//Toolkit.getDefaultToolkit().beep();
		textToggle=!textToggle;
		lastPressed1 = System.currentTimeMillis();
	}

	/* Keys 1 and 2 can be customized for whatever purpose if you want to */
	public void toggleCustom2() {
		System.out.println("Custom key 2 pressed!");
	}

	public void pause() {
		paused = true;
		resetRockets();
	}

	public void run() {
		paused = false;
	}
}