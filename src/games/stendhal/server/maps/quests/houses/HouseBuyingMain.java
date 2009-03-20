package games.stendhal.server.maps.quests.houses;

import games.stendhal.common.MathHelper;
import games.stendhal.server.core.engine.SingletonRepository;
import games.stendhal.server.core.engine.StendhalRPZone;
import games.stendhal.server.core.events.LoginListener;
import games.stendhal.server.core.pathfinder.FixedPath;
import games.stendhal.server.core.pathfinder.Node;
import games.stendhal.server.entity.item.Item;
import games.stendhal.server.entity.item.HouseKey;
import games.stendhal.server.entity.item.StackableItem;
import games.stendhal.server.entity.mapstuff.chest.StoredChest;
import games.stendhal.server.entity.mapstuff.portal.HousePortal;
import games.stendhal.server.entity.npc.ChatAction;
import games.stendhal.server.entity.npc.ChatCondition;
import games.stendhal.server.entity.npc.ConversationPhrases;
import games.stendhal.server.entity.npc.ConversationStates;
import games.stendhal.server.entity.npc.SpeakerNPC;
import games.stendhal.server.entity.npc.condition.AgeGreaterThanCondition;
import games.stendhal.server.entity.npc.condition.AndCondition;
import games.stendhal.server.entity.npc.condition.NotCondition;
import games.stendhal.server.entity.npc.condition.QuestCompletedCondition;
import games.stendhal.server.entity.npc.condition.QuestNotCompletedCondition;
import games.stendhal.server.entity.npc.condition.QuestNotStartedCondition;
import games.stendhal.server.entity.npc.condition.TextHasNumberCondition;
import games.stendhal.server.entity.npc.parser.JokerExprMatcher;
import games.stendhal.server.entity.npc.parser.Sentence;
import games.stendhal.server.entity.player.Player;
import games.stendhal.server.maps.quests.AbstractQuest;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;

/**
 * Controls house buying.
 *
 * @author kymara
 */

public class HouseBuyingMain extends AbstractQuest implements LoginListener {


	/** the logger instance. */
	private static final Logger logger = Logger.getLogger(HouseBuyingMain.class);

	// constants
	private static final String QUEST_SLOT = "house";

	private static final String PRINCESS_QUEST_SLOT = "imperial_princess";
	private static final String ANNA_QUEST_SLOT = "toys_collector";
	private static final String KEYRING_QUEST_SLOT = "hungry_joshua";
	private static final String GHOSTS_QUEST_SLOT = "find_ghosts";
	private static final String DAILY_ITEM_QUEST_SLOT = "daily_item";
	private static final String FISHROD_QUEST_SLOT = "get_fishing_rod";
	private static final String ZARA_QUEST_SLOT = "suntan_cream_zara";
	private static final String KIRDNEH_QUEST_SLOT = "weekly_item";

	/** Cost to buy house in kalavan */
	private static final int COST_KALAVAN = 100000;
	/** Cost to buy house in ados */
	private static final int COST_ADOS = 120000;
	/** Cost to buy house in kirdneh */
	private static final int COST_KIRDNEH = 120000;

	/** Cost to buy spare keys */
	private static final int COST_OF_SPARE_KEY = 1000;

	/** percentage of initial cost refunded when you resell a house*/
	private static final int DEPRECIATION_PERCENTAGE = 40;
	
	/**
	 * age required to buy a house. Note, age is in minutes, not seconds! So
	 * this is 300 hours
	 */
	private static final int REQUIRED_AGE = 300 * 60;

	/** Zone name */
	private static final String KALAVAN_CITY = "0_kalavan_city";
	/** Zone name */
	private static final String KIRDNEH_CITY = "0_kirdneh_city";
	/** Zone name */
	private static final String ADOS_CITY_N = "0_ados_city_n";
	/** Zone name */
	private static final String ADOS_CITY = "0_ados_city";
	/** Zone name */
	private static final String ADOS_TOWNHALL = "int_ados_town_hall_3";
	/** Zone name */
	private static final String KIRDNEH_TOWNHALL = "int_kirdneh_townhall";

	protected SpeakerNPC npc;
	protected SpeakerNPC npc2;
	protected SpeakerNPC npc3;

	protected StendhalRPZone kalavan_city_zone;
	protected StendhalRPZone kirdneh_city_zone;
	protected StendhalRPZone ados_city_zone;
	protected StendhalRPZone ados_city_n_zone;
	protected StendhalRPZone ados_townhall_zone;
	protected StendhalRPZone kirdneh_townhall_zone;
	
	private HouseTax houseTax;

	@Override
	public void init(final String name) {
		super.init(name);
	}
	@Override
	public String getSlotName() {
		return QUEST_SLOT;
	}

	private final class BuyHouseChatAction implements ChatAction {

		private final String location;

		/**
		 * Creates a new BuyHouseChatAction.
		 * 
		 * @param location
		 *            where are the houses?
		 */
		private BuyHouseChatAction(final String location) {
			this.location = location;
		}

		public void fire(final Player player, final Sentence sentence, final SpeakerNPC engine) {

			final int cost = getCost(location);
			final int number = sentence.getNumeral().getAmount();
			// now check if the house they said is free
			final String itemName = Integer.toString(number);

			final HousePortal houseportal = HouseUtilities.getHousePortal(number);

			if (houseportal == null) {
				// something bad happened
				engine.say("Sorry I did not understand you, could you try saying the house number you want again please?");
				engine.setCurrentState(ConversationStates.QUEST_OFFERED);
				return;
			}

			final String owner = houseportal.getOwner();
			if (owner.length() == 0) {
				
				// it's available, so take money
				if (player.isEquipped("money", cost)) {
					final Item key = SingletonRepository.getEntityManager().getItem(
																					"house key");
					final String doorId = location + " house " + itemName;

					final int locknumber = houseportal.getLockNumber();
					((HouseKey) key).setup(doorId, locknumber, player.getName());
				
					if (player.equipToInventoryOnly(key)) {
						engine.say("Congratulations, here is your key to house "
								   + itemName
								   + "! Make sure you change the locks if you ever lose it. Do you want to buy a spare key, at a price of "
								   + COST_OF_SPARE_KEY + " money?");
						
						player.drop("money", cost);
						// remember what house they own
						player.setQuest(QUEST_SLOT, itemName);

						// put nice things and a helpful note in the chest
						fillChest(HouseUtilities.findChest(houseportal));

						// set the time so that the taxman can start harassing the player
						final long time = System.currentTimeMillis();
						houseportal.setExpireTime(time);

						houseportal.setOwner(player.getName());
						engine.setCurrentState(ConversationStates.QUESTION_1);
					} else {
						engine.say("Sorry, you can't carry more keys!");
					}
				
				} else {
					engine.say("You do not have enough money to buy a house!");
				}
			
			} else {
				engine.say("Sorry, house " + itemName
						   + " is sold, please give me the number of another.");
				engine.setCurrentState(ConversationStates.QUEST_OFFERED);
			}
		}
	}
	
	private void fillChest(StoredChest chest) {
		Item item = SingletonRepository.getEntityManager().getItem("note");
		item.setDescription("WELCOME TO THE HOUSE OWNER\n"
				+ "1. If you do not pay your house taxes, the house and all the items in the chest will be confiscated.\n"
				+ "2. All people who can get in the house can use the chest.\n"
				+ "3. Remember to change your locks as soon as the security of your house is compromised.\n"
				+ "4. You can resell your house to the state if wished (please don't leave me)\n");
		chest.add(item);
		
		item = SingletonRepository.getEntityManager().getItem("wine");
		((StackableItem) item).setQuantity(2);
		chest.add(item);
		
		item = SingletonRepository.getEntityManager().getItem("chocolate bar");
		((StackableItem) item).setQuantity(2);
		chest.add(item);
	}

	private int getCost(final String location) {
		// TODO: think how to do this nicely, or remove this TODO
		if ("ados".equals(location)) {
			return COST_ADOS;
		} else if ("kalavan".equals(location)) {
			return COST_KALAVAN;
		} else if ("kirdneh".equals(location)) {
			return COST_KIRDNEH;
		} else {
			logger.error("getCost got passed a bad location, " + location);
			return COST_KIRDNEH;
		}
	}

	/** The sale of a spare key has been agreed, player meets conditions, here is the action to simply sell it */
	private final class BuySpareKeyChatAction implements ChatAction {
	
		private final String location;

		/**
		 * Creates a new  BuySpareKeyChatAction.
		 * 
		 * @param location
		 *            where are the houses?
		 */
		private BuySpareKeyChatAction(final String location) {
			this.location = location;
		}

		public void fire(final Player player, final Sentence sentence, final SpeakerNPC engine) {
			if (player.isEquipped("money", COST_OF_SPARE_KEY)) {

				final String housenumber = player.getQuest(QUEST_SLOT);
				final Item key = SingletonRepository.getEntityManager().getItem(
																				"house key");
				final String doorId = location + " house " + housenumber;
				final int number = MathHelper.parseInt(housenumber);
				final HousePortal houseportal = HouseUtilities.getHousePortal(number);

				if (houseportal == null) {
					// something bad happened
					engine.say("Sorry something bad happened. I'm terribly embarassed.");
					return;
				}
				
				final int locknumber = houseportal.getLockNumber();

				((HouseKey) key).setup(doorId, locknumber, player.getName());

				if (player.equipToInventoryOnly(key)) {
					player.drop("money", COST_OF_SPARE_KEY);
					engine.say("Here you go, a spare key to your house. Please remember, only give spare keys to people you #really, #really, trust! Anyone with a spare key can access your chest, and tell anyone that you give a key to, to let you know if they lose it. If that happens, you should #change your locks.");
				} else {
					engine.say("Sorry, you can't carry more keys!");
				}
			} else {
				engine.say("You do not have enough money for another key!");
			}
		}
	}

	private final class ResellHouseAction implements ChatAction {

		public void fire(final Player player, final Sentence sentence, final SpeakerNPC engine) {

			// we need to find out where this house is so we know how much to refund them
			String location = "";
			final String claimedHouse = player.getQuest(QUEST_SLOT);
		
			try {
				final int id = Integer.parseInt(claimedHouse);
				final HousePortal portal = HouseUtilities.getHousePortal(id);
				final String doorId = portal.getDoorId();
				final String[] parts = doorId.split(" ");
				
				location = parts[0];
				final int cost = (getCost(location) * DEPRECIATION_PERCENTAGE) / 100 - houseTax.getTaxDebt(portal);
				final StackableItem money = (StackableItem) SingletonRepository.getEntityManager().getItem("money");
				money.setQuantity(cost);
				player.equipOrPutOnGround(money);
		
				portal.changeLock();
				portal.setOwner("");
				// the player has sold the house. clear the slot
				player.removeQuest(QUEST_SLOT);
				engine.say("Thanks, here is your " + Integer.toString(cost)
						   + " money owed, from the house value, minus any owed taxes. Now that you don't own a house "
						   + "you would be free to buy another if you want to.");
			} catch (final NumberFormatException e) {
				logger.error("Invalid number in house slot", e);
				engine.say("Sorry, something bad happened. I'm terribly embarassed.");
				return;
			}
		}
	}

	private final class ChangeLockAction implements ChatAction {

		public void fire(final Player player, final Sentence sentence, final SpeakerNPC engine) {
			if (player.isEquipped("money", COST_OF_SPARE_KEY)) {
				// we need to find out which this houseportal is so we can change lock
				final String claimedHouse = player.getQuest(QUEST_SLOT);
				
				try {
					final int id = Integer.parseInt(claimedHouse);
					final HousePortal portal = HouseUtilities.getHousePortal(id);
					// change the lock
					portal.changeLock();
					// make a new key for the player, with the new locknumber
					final String doorId = portal.getDoorId();
					final Item key = SingletonRepository.getEntityManager().getItem("house key");
					final int locknumber = portal.getLockNumber();

					((HouseKey) key).setup(doorId, locknumber, player.getName());
					if (player.equipToInventoryOnly(key)) {
						player.drop("money", COST_OF_SPARE_KEY);
						engine.say("The locks have been changed for " + doorId + ", here is your new key. Do you want to buy a spare key, at a price of "
								   + COST_OF_SPARE_KEY + " money?");
						engine.setCurrentState(ConversationStates.QUESTION_1);
					} else {
						// if the player doesn't have the space for the key, change the locks anyway as a security measure, but don't charge.
						engine.say("The locks have been changed for " 
								   + doorId + ", but you do not have space to carry the new key. I haven't charged you for this service. "
								   + "If you want to go away and make space, come back and I will offer you the chance to buy a spare key. Goodbye.");
						engine.setCurrentState(ConversationStates.IDLE);
					}
				} catch (final NumberFormatException e) {
					logger.error("Invalid number in house slot", e);
					engine.say("Sorry, something bad happened. I'm terribly embarassed.");
					return;
				}
			} else { 
				engine.say("You need to pay " + COST_OF_SPARE_KEY + " money to change the lock and get a new key for your house.");
			}
		}
	}

	/** House owners are offered the chance to buy a spare key when the seller greets them. Others are just greeted with their name. */
	private final class HouseSellerGreetingAction implements ChatAction {
		public void fire(final Player player, final Sentence sentence, final SpeakerNPC engine) {
			String reply = "";
			if (HouseUtilities.playerOwnsHouse(player)) {
				reply = " At the cost of "
					+ COST_OF_SPARE_KEY
					+ " money you can purchase a spare key for your house. Do you want to buy one now?";
				engine.setCurrentState(ConversationStates.QUESTION_1);
			} else if (player.hasQuest(QUEST_SLOT)) {
				// the player has lost the house. clear the slot so that he can buy a new one if he wants
				player.removeQuest(QUEST_SLOT);
			}
			
			engine.say("Hello, " + player.getTitle() + "." + reply);
		}
		
	}

	class PlayerOwnsHouseCondition implements ChatCondition {
		public boolean fire(final Player player, final Sentence sentence, final SpeakerNPC npc) {
			return HouseUtilities.playerOwnsHouse(player);
		}
	}

	// this will be ideal for a seller to list all unbought houses
	// using Grammar.enumerateCollection
	private List<String> getUnboughtHouses() {
	    final List<String> unbought = new LinkedList<String>();
		final List<HousePortal> portals =  HouseUtilities.getHousePortals();
		for (final HousePortal houseportal : portals) {
			final String owner = houseportal.getOwner();
			if (owner.length() == 0) {
				unbought.add(houseportal.getDoorId());
			}
		}
		return unbought;
	}

	/** The NPC for Kalavan Houses */
	private void createNPC() {
		npc = new SpeakerNPC("Barrett Holmes") {
			@Override
			protected void createPath() {
				final List<Node> nodes = new LinkedList<Node>();
				nodes.add(new Node(55, 94));
				nodes.add(new Node(93, 94));
				nodes.add(new Node(93, 73));
				nodes.add(new Node(107, 73));
				nodes.add(new Node(107, 35));
				nodes.add(new Node(84, 35));
				nodes.add(new Node(84, 20));
				nodes.add(new Node(17, 20));
				nodes.add(new Node(17, 82));
				nodes.add(new Node(43, 82));
				nodes.add(new Node(43, 94));
				setPath(new FixedPath(nodes, true));
			}

			@Override
			protected void createDialog() {
				addGreeting(null, new HouseSellerGreetingAction());

				// quest slot 'house' is started so player owns a house
				add(ConversationStates.ATTENDING, 
					Arrays.asList("cost", "house"),
					new PlayerOwnsHouseCondition(),
					ConversationStates.ATTENDING, 
					"As you already know, the cost of a new house is "
					+ COST_KALAVAN
					+ " money. But you cannot own more than one house, the market is too demanding for that! You cannot own another house until you #resell the one you already own.",
					null);

				// Other than the condition that you must not already own a house, there are a number of conditions a player must satisfy. 
				// For definiteness we will check these conditions in a set order. 
				// So then the NPC doesn't have to choose which reason to reject the player for (appears as a WARN from engine if he has to choose)

				// player has not done required quest, hasn't got a house at all
				add(ConversationStates.ATTENDING, 
					Arrays.asList("cost", "house"),
					new AndCondition(new QuestNotStartedCondition(QUEST_SLOT), new QuestNotCompletedCondition(PRINCESS_QUEST_SLOT)),
					ConversationStates.ATTENDING, 
					"The cost of a new house is "
					+ COST_KALAVAN
					+ " money. But I am afraid I cannot sell you a house until your citizenship has been approved by the King, who you will find "
					+ " north of here in Kalavan Castle. Try speaking to his daughter first, she is ... friendlier.",
					null);

					// player is not old enough but they have doen princess quest 
				// (don't need to check if they have a house, they can't as they're not old enough)
				add(ConversationStates.ATTENDING, 
					Arrays.asList("cost", "house"),
					new AndCondition(
									 new QuestCompletedCondition(PRINCESS_QUEST_SLOT),
									 new NotCondition(new AgeGreaterThanCondition(REQUIRED_AGE))),
					ConversationStates.ATTENDING, 
					"The cost of a new house is "
					+ COST_KALAVAN
					+ " money. But I am afraid I cannot trust you with house ownership just yet, come back when you have spent at least " 
					+ Integer.toString((REQUIRED_AGE / 60)) + " hours on Faiumoni.",
					null);

				// player is eligible to buy a house
				add(ConversationStates.ATTENDING, 
					Arrays.asList("cost", "house"),
					new AndCondition(new QuestNotStartedCondition(QUEST_SLOT), 
									 new AgeGreaterThanCondition(REQUIRED_AGE), 
									 new QuestCompletedCondition(PRINCESS_QUEST_SLOT)),
					ConversationStates.QUEST_OFFERED, 
					"The cost of a new house is "
					+ COST_KALAVAN
					+ " money. Also, you must pay a house tax of " + HouseTax.BASE_TAX
					+ " money, every month. If you have a house in mind, please tell me the number now. I will check availability.",
					null);

				// handle house numbers 1 to 25
				add(ConversationStates.QUEST_OFFERED,
					// match for all numbers as trigger expression
					"NUM", new JokerExprMatcher(),
					new TextHasNumberCondition(1, 25),
					ConversationStates.ATTENDING,
					null,
					new BuyHouseChatAction("kalavan"));

				// we need to warn people who buy spare keys about the house
				// being accessible to other players with a key
				add(ConversationStates.QUESTION_1,
					ConversationPhrases.YES_MESSAGES,
					null,
					ConversationStates.QUESTION_2,
					"Before we go on, I must warn you that anyone with a key to your house can enter it, and access the items in the chest in your house. Do you still wish to buy a spare key?",
					null);

				// player wants spare keys and is OK with house being accessible
				// to other person.
				add(ConversationStates.QUESTION_2,
					ConversationPhrases.YES_MESSAGES, 
					null,
					ConversationStates.ATTENDING, 
					null,
					new BuySpareKeyChatAction("kalavan"));
				
				// refused offer to buy spare key for security reasons
				add(ConversationStates.QUESTION_2,
					ConversationPhrases.NO_MESSAGES,
					null,
					ConversationStates.ATTENDING,
					"That is wise of you. It is certainly better to restrict use of your house to those you can really trust.",
					null);

				// refused offer to buy spare key 
				add(ConversationStates.QUESTION_1,
					ConversationPhrases.NO_MESSAGES,
					null,
					ConversationStates.ATTENDING,
					"No problem! Just so you know, if you need to #change your locks, I can do that, and you can also #resell your house to me if you want to.",
					null);

				// player is eligible to resell a house
				add(ConversationStates.ATTENDING, 
					Arrays.asList("resell", "sell"),
					new PlayerOwnsHouseCondition(),
					ConversationStates.QUESTION_3, 
					"The state will pay you "
					+ Integer.toString(DEPRECIATION_PERCENTAGE)
					+ " percent of the price you paid for your house, minus any taxes you owe. You should remember to collect any belongings from your house before you sell it. Do you really want to sell your house to the state?",
					null);

				// player is not eligible to resell a house
				add(ConversationStates.ATTENDING, 
					Arrays.asList("resell", "sell"),
					new NotCondition(new PlayerOwnsHouseCondition()),
					ConversationStates.ATTENDING, 
					"You don't own any house at the moment. If you want to buy one please ask about the #cost.",
					null);

				// accepted offer to resell a house
				add(ConversationStates.QUESTION_3,
					ConversationPhrases.YES_MESSAGES,
					null,
					ConversationStates.ATTENDING,
					null,
					new ResellHouseAction());

				// refused offer to resell a house
				add(ConversationStates.QUESTION_3,
					ConversationPhrases.NO_MESSAGES,
					null,
					ConversationStates.ATTENDING,
					"Well, I'm glad you changed your mind.",
					null);

				// player is eligible to change locks
				add(ConversationStates.ATTENDING, 
					"change",
					new PlayerOwnsHouseCondition(),
					ConversationStates.SERVICE_OFFERED, 
					"If you are at all worried about the security of your house or, don't trust anyone you gave a spare key to, "
					+ "it is wise to change your locks. Do you want me to change your house lock and give you a new key now?",
					null);

				// player is not eligible to change locks
				add(ConversationStates.ATTENDING, 
					"change",
					new NotCondition(new PlayerOwnsHouseCondition()),
					ConversationStates.ATTENDING, 
					"You don't own any house at the moment. If you want to buy one please ask about the #cost.",
					null);

				// accepted offer to change locks
				add(ConversationStates.SERVICE_OFFERED,
					ConversationPhrases.YES_MESSAGES,
					null,
					ConversationStates.ATTENDING,
					null,
					new ChangeLockAction());

				// refused offer to change locks 
				add(ConversationStates.SERVICE_OFFERED,
					ConversationPhrases.NO_MESSAGES,
					null,
					ConversationStates.ATTENDING,
					"OK, if you're really sure. Please let me know if I can help with anything else.",
					null);

				addJob("I'm an estate agent. In simple terms, I sell houses to those who have been granted #citizenship. They #cost a lot, of course. Our brochure is at #http://arianne.sourceforge.net/wiki/index.php?title=StendhalHouses.");
				addReply("citizenship",
						"The royalty in Kalavan Castle decide that.");
				addReply(
						"buy",
						"You should really enquire the #cost before you ask to buy. And check our brochure, #http://arianne.sourceforge.net/wiki/index.php?title=StendhalHouses.");
				addReply("really",
						"That's right, really, really, really. Really.");
				addOffer("I sell houses, please look at #http://arianne.sourceforge.net/wiki/index.php?title=StendhalHouses for examples of how they look inside. Then ask about the #cost when you are ready.");
				addHelp("You may be eligible to buy a house if there are any available. If you can pay the #cost, I'll give you a key. As a house owner you can buy spare keys to give your friends. See #http://arianne.sourceforge.net/wiki/index.php?title=StendhalHouses for pictures inside the houses and more details.");
				addQuest("You may buy houses from me, please ask the #cost if you are interested. Perhaps you would first like to view our brochure, #http://arianne.sourceforge.net/wiki/index.php?title=StendhalHouses.");
				addGoodbye("Goodbye.");
			}
		};

		npc.setDescription("You see a smart looking man.");
		npc.setEntityClass("estateagentnpc");
		npc.setPosition(55, 94);
		npc.initHP(100);
		kalavan_city_zone.add(npc);
	}

	/** The NPC for Ados Houses */
	private void createNPC2() {
		npc2 = new SpeakerNPC("Reg Denson") {
			@Override
			protected void createPath() {
				final List<Node> nodes = new LinkedList<Node>();
				nodes.add(new Node(37, 13));
				nodes.add(new Node(31, 13));
				nodes.add(new Node(31, 10));
				nodes.add(new Node(35, 10));
				nodes.add(new Node(35, 4));
				nodes.add(new Node(25, 4));
				nodes.add(new Node(25, 15));
				nodes.add(new Node(15, 15));
				nodes.add(new Node(15, 9));
				nodes.add(new Node(18, 9));
				nodes.add(new Node(18, 4));
				nodes.add(new Node(18, 10));
				nodes.add(new Node(15, 10));
				nodes.add(new Node(15, 16));
				nodes.add(new Node(25, 16));
				nodes.add(new Node(25, 3));
				nodes.add(new Node(35, 3));
				nodes.add(new Node(35, 10));
				nodes.add(new Node(37, 10));
				setPath(new FixedPath(nodes, true));
			}

			@Override
			protected void createDialog() {
				addGreeting(null, new HouseSellerGreetingAction());
				
				// quest slot 'house' is started so player owns a house
				add(ConversationStates.ATTENDING, 
					Arrays.asList("cost", "house"),
					new PlayerOwnsHouseCondition(),
					ConversationStates.ATTENDING, 
					"As you already know, the cost of a new house in Ados is "
					+ COST_ADOS
					+ " money. But you cannot own more than one house, the market is too demanding for that! You cannot own another house until you #resell the one you already own.",
					null);

				// Other than the condition that you must not already own a house, there are a number of conditions a player must satisfy. 
				// For definiteness we will check these conditions in a set order. 
				// So then the NPC doesn't have to choose which reason to reject the player for (appears as a WARN from engine if he has to choose)

				// player is not old enough
				add(ConversationStates.ATTENDING, 
					Arrays.asList("cost", "house"),
					new NotCondition(new AgeGreaterThanCondition(REQUIRED_AGE)),
					ConversationStates.ATTENDING,
					"The cost of a new house in Ados is "
					+ COST_ADOS
					+ " money. But I am afraid I cannot trust you with house ownership just yet, come back when you have spent at least " 
					+ Integer.toString((REQUIRED_AGE / 60)) + " hours on Faiumoni.",
					null);


				// player doesn't have a house and is old enough but has not done required quests
				add(ConversationStates.ATTENDING, 
					Arrays.asList("cost", "house"),
					new AndCondition(new AgeGreaterThanCondition(REQUIRED_AGE), 
									 new QuestNotStartedCondition(QUEST_SLOT),
									 new NotCondition(
													  new AndCondition(
																	   new QuestCompletedCondition(DAILY_ITEM_QUEST_SLOT),
																	   new QuestCompletedCondition(ANNA_QUEST_SLOT),
																	   new QuestCompletedCondition(KEYRING_QUEST_SLOT),
																	   new QuestCompletedCondition(FISHROD_QUEST_SLOT),
																	   new QuestCompletedCondition(GHOSTS_QUEST_SLOT),
																	   new QuestCompletedCondition(ZARA_QUEST_SLOT)))),
					ConversationStates.ATTENDING, 
					"The cost of a new house in Ados is "
					+ COST_ADOS
					+ " money. But I am afraid I cannot sell you a house yet as you must first prove yourself a worthy #citizen.",
					null);

				// player is eligible to buy a house
				add(ConversationStates.ATTENDING, 
					Arrays.asList("cost", "house"),
					new AndCondition(new QuestNotStartedCondition(QUEST_SLOT), 
									 new AgeGreaterThanCondition(REQUIRED_AGE), 
									 new QuestCompletedCondition(DAILY_ITEM_QUEST_SLOT),
									 new QuestCompletedCondition(ANNA_QUEST_SLOT),
									 new QuestCompletedCondition(KEYRING_QUEST_SLOT),
									 new QuestCompletedCondition(FISHROD_QUEST_SLOT),
									 new QuestCompletedCondition(GHOSTS_QUEST_SLOT),
									 new QuestCompletedCondition(ZARA_QUEST_SLOT)),
					ConversationStates.QUEST_OFFERED, 
					"The cost of a new house in Ados is "
					+ COST_ADOS
					+ " money. Also, you must pay a house tax of " + HouseTax.BASE_TAX
					+ " money, every month. If you have a house in mind, please tell me the number now. I will check availability. "
					+ "The Ados houses are numbered from 50 to 68.",
					null);

				// handle house numbers 50 to 68
				add(ConversationStates.QUEST_OFFERED,
					// match for all numbers as trigger expression
					"NUM", new JokerExprMatcher(),
					new TextHasNumberCondition(50, 68),
					ConversationStates.ATTENDING, 
					null,
					new BuyHouseChatAction("ados"));

				// we need to warn people who buy spare keys about the house
				// being accessible to other players with a key
				add(ConversationStates.QUESTION_1,
					ConversationPhrases.YES_MESSAGES,
					null,
					ConversationStates.QUESTION_2,
					"Before we go on, I must warn you that anyone with a key to your house can enter it, and access the items in the chest in your house. Do you still wish to buy a spare key?",
					null);

				// player wants spare keys and is OK with house being accessible
				// to other person.
				add(ConversationStates.QUESTION_2,
					ConversationPhrases.YES_MESSAGES, 
					null,
					ConversationStates.ATTENDING, 
					null,
					new BuySpareKeyChatAction("ados"));

				// Refused the offer to buy a spare key for security reasons
				add(ConversationStates.QUESTION_2,
					ConversationPhrases.NO_MESSAGES,
					null,
					ConversationStates.ATTENDING,
					"That is wise of you. It is certainly better to restrict use of your house to those you can really trust.",
					null);

				// Refused the offer to buy a spare key
				add(ConversationStates.QUESTION_1,
					ConversationPhrases.NO_MESSAGES,
					null,
					ConversationStates.ATTENDING,
					"No problem! Just so you know, if you need to #change your locks, I can do that, and you can also #resell your house to me if you want to.",
					null);

				// player is eligible to resell a house
				add(ConversationStates.ATTENDING, 
					Arrays.asList("resell", "sell"),
					new PlayerOwnsHouseCondition(),
					ConversationStates.QUESTION_3, 
					"The state will pay you "
					+ Integer.toString(DEPRECIATION_PERCENTAGE)
					+ " percent of the price you paid for your house, minus any taxes you owe. You should remember to collect any belongings from your house before you sell it. Do you really want to sell your house to the state?",
					null);

				// player is not eligible to resell a house
				add(ConversationStates.ATTENDING, 
					Arrays.asList("resell", "sell"),
					new NotCondition(new PlayerOwnsHouseCondition()),
					ConversationStates.ATTENDING, 
					"You don't own any house at the moment. If you want to buy one please ask about the #cost.",
					null);

				// accepted offer to resell a house
				add(ConversationStates.QUESTION_3,
					ConversationPhrases.YES_MESSAGES,
					null,
					ConversationStates.ATTENDING,
					null,
					new ResellHouseAction());

				// refused offer to resell a house
				add(ConversationStates.QUESTION_3,
					ConversationPhrases.NO_MESSAGES,
					null,
					ConversationStates.ATTENDING,
					"Well, I'm glad you changed your mind.",
					null);

				// player is eligible to change locks
				add(ConversationStates.ATTENDING, 
					"change",
					new PlayerOwnsHouseCondition(),
					ConversationStates.SERVICE_OFFERED, 
					"If you are at all worried about the security of your house or, don't trust anyone you gave a spare key to, "
					+ "it is wise to change your locks. Do you want me to change your house lock and give you a new key now?",
					null);

				// player is not eligible to change locks
				add(ConversationStates.ATTENDING, 
					"change",
					new NotCondition(new PlayerOwnsHouseCondition()),
					ConversationStates.ATTENDING, 
					"You don't own any house at the moment. If you want to buy one please ask about the #cost.",
					null);

				// accepted offer to change locks
				add(ConversationStates.SERVICE_OFFERED,
					ConversationPhrases.YES_MESSAGES,
					null,
					ConversationStates.ATTENDING,
					null,
					new ChangeLockAction());

				// refused offer to change locks 
				add(ConversationStates.SERVICE_OFFERED,
					ConversationPhrases.NO_MESSAGES,
					null,
					ConversationStates.ATTENDING,
					"OK, if you're really sure. Please let me know if I can help with anything else.",
					null);

				addJob("I'm an estate agent. In simple terms, I sell houses for the city of Ados. Please ask about the #cost if you are interested. Our brochure is at #http://arianne.sourceforge.net/wiki/index.php?title=StendhalHouses.");
                addReply("citizen", "I conduct an informal survey amongst the Ados residents. If you have helped everyone in Ados, I see no reason why they shouldn't recommend you. I speak with my friend Joshua, the Mayor, the little girl Anna, Pequod the fisherman, Zara, and I even commune with Carena, of the spirit world. Together they give a reliable opinion.");
				addReply("buy",	"You may wish to know the #cost before you buy. Perhaps our brochure, #http://arianne.sourceforge.net/wiki/index.php?title=StendhalHouses would also be of interest.");
				addReply("really", "That's right, really, really, really. Really.");
				addOffer("I sell Ados houses, please look at #http://arianne.sourceforge.net/wiki/index.php?title=StendhalHouses for examples of how they look inside. Then ask about the #cost when you are ready.");
				addHelp("You may be eligible to become a #citizen. Of course there must also be houses available in Ados. If you can pay the #cost, I'll give you a key. As a house owner you can buy spare keys to give your friends. See #http://arianne.sourceforge.net/wiki/index.php?title=StendhalHouses for pictures inside the houses and more details.");
				addQuest("You may buy houses from me, please ask the #cost if you are interested. Perhaps you would first like to view our brochure, #http://arianne.sourceforge.net/wiki/index.php?title=StendhalHouses.");
				addGoodbye("Goodbye.");
			}
		};

		npc2.setDescription("You see a smart looking man.");
		npc2.setEntityClass("estateagent2npc");
		npc2.setPosition(37, 13);
		npc2.initHP(100);
		ados_townhall_zone.add(npc2);
	}

	/** The NPC for Kirdneh Houses */
	private void createNPC3() {
		npc3 = new SpeakerNPC("Roger Frampton") {
			@Override
			protected void createPath() {
				setPath(null);
			}

			@Override
			protected void createDialog() {
				addGreeting(null, new HouseSellerGreetingAction());

				// quest slot 'house' is started so player owns a house
				add(ConversationStates.ATTENDING, 
					Arrays.asList("cost", "house"),
					new PlayerOwnsHouseCondition(),
					ConversationStates.ATTENDING, 
					"In Kirdneh the cost of a new house is "
					+ COST_KIRDNEH
					+ " money. But you cannot own another house until you #resell the one you already own.",
					null);

				// Other than the condition that you must not already own a house, there are a number of conditions a player must satisfy. 
				// For definiteness we will check these conditions in a set order. 
				// So then the NPC doesn't have to choose which reason to reject the player for (appears as a WARN from engine if he has to choose)

				// player is not old enough
				add(ConversationStates.ATTENDING, 
					Arrays.asList("cost", "house"),
					new NotCondition(new AgeGreaterThanCondition(REQUIRED_AGE)),
					ConversationStates.ATTENDING, 
					"The cost of a new house in Kirdneh is "
					+ COST_KIRDNEH
					+ " money. But I am afraid I cannot trust you with house ownership just yet. Come back when you have spent at least " 
					+ Integer.toString((REQUIRED_AGE / 60)) + " hours on Faiumoni.",
					null);

				// player is old enough and hasn't got a house but has not done required quest
				add(ConversationStates.ATTENDING, 
					Arrays.asList("cost", "house"),
					new AndCondition(new AgeGreaterThanCondition(REQUIRED_AGE), 
									 new QuestNotCompletedCondition(KIRDNEH_QUEST_SLOT), 
									 new QuestNotStartedCondition(QUEST_SLOT)),
					ConversationStates.ATTENDING, 
					"The cost of a new house in Kirdneh is "
					+ COST_KIRDNEH
					+ " money. But my principle is never to sell a house without establishing first the good #reputation of the prospective buyer.",
					null);

				// player is eligible to buy a house
				add(ConversationStates.ATTENDING, 
					Arrays.asList("cost", "house"),
					new AndCondition(new QuestNotStartedCondition(QUEST_SLOT), 
									 new AgeGreaterThanCondition(REQUIRED_AGE), 
									 new QuestCompletedCondition(KIRDNEH_QUEST_SLOT)),
					ConversationStates.QUEST_OFFERED, 
					"The cost of a new house is "
					+ COST_KIRDNEH
					+ " money.  Also, you must pay a house tax of " + HouseTax.BASE_TAX
					+ " money, every month. If you have a house in mind, please tell me the number now. I will check availability. "
					+ "Kirdneh Houses are numbered 26 to 49.",
					null);

				// handle house numbers 26 to 49
				add(ConversationStates.QUEST_OFFERED,
					// match for all numbers as trigger expression
					"NUM", new JokerExprMatcher(),
					new TextHasNumberCondition(26, 49),
					ConversationStates.ATTENDING, 
					null,
					new BuyHouseChatAction("kirdneh"));

				// we need to warn people who buy spare keys about the house
				// being accessible to other players with a key
				add(ConversationStates.QUESTION_1,
					ConversationPhrases.YES_MESSAGES,
					null,
					ConversationStates.QUESTION_2,
					"Before we go on, I must warn you that anyone with a key to your house can enter it, and access the items in the chest in your house. Do you still wish to buy a spare key?",
					null);

				// player wants spare keys and is OK with house being accessible
				// to other person.
				add(ConversationStates.QUESTION_2,
					ConversationPhrases.YES_MESSAGES, 
					null,
					ConversationStates.ATTENDING, 
					null,
					new BuySpareKeyChatAction("kirdneh"));

				// Refused the offer to buy a spare key for security reasons
				add(ConversationStates.QUESTION_2,
					ConversationPhrases.NO_MESSAGES,
					null,
					ConversationStates.ATTENDING,
					"That is wise of you. It is certainly better to restrict use of your house to those you can really trust.",
					null);

				// Refused the offer to buy a spare key
				add(ConversationStates.QUESTION_1,
					ConversationPhrases.NO_MESSAGES,
					null,
					ConversationStates.ATTENDING,
					"No problem! Just so you know, if you need to #change your locks, I can do that, and you can also #resell your house to me if you want to.",
					null);

				// player is eligible to resell a house
				add(ConversationStates.ATTENDING, 
					Arrays.asList("resell", "sell"),
					new PlayerOwnsHouseCondition(),
					ConversationStates.QUESTION_3, 
					"The state will pay you "
					+ Integer.toString(DEPRECIATION_PERCENTAGE)
					+ " percent of the price you paid for your house, minus any taxes you owe. You should remember to collect any belongings from your house before you sell it. Do you really want to sell your house to the state?",
					null);

				// player is not eligible to resell a house
				add(ConversationStates.ATTENDING, 
					Arrays.asList("resell", "sell"),
					new NotCondition(new PlayerOwnsHouseCondition()),
					ConversationStates.ATTENDING, 
					"You don't own any house at the moment. If you want to buy one please ask about the #cost.",
					null);

				// accepted offer to resell a house
				add(ConversationStates.QUESTION_3,
					ConversationPhrases.YES_MESSAGES,
					null,
					ConversationStates.ATTENDING,
					null,
					new ResellHouseAction());

				// refused offer to resell a house
				add(ConversationStates.QUESTION_3,
					ConversationPhrases.NO_MESSAGES,
					null,
					ConversationStates.ATTENDING,
					"Well, I'm glad you changed your mind.",
					null);

				// player is eligible to change locks
				add(ConversationStates.ATTENDING, 
					"change",
					new PlayerOwnsHouseCondition(),
					ConversationStates.SERVICE_OFFERED, 
					"If you are at all worried about the security of your house or, don't trust anyone you gave a spare key to, "
					+ "it is wise to change your locks. Do you want me to change your house lock and give you a new key now?",
					null);

				// player is not eligible to change locks
				add(ConversationStates.ATTENDING, 
					"change",
					new NotCondition(new PlayerOwnsHouseCondition()),
					ConversationStates.ATTENDING, 
					"You don't own any house at the moment. If you want to buy one please ask about the #cost.",
					null);

				// accepted offer to change locks
				add(ConversationStates.SERVICE_OFFERED,
					ConversationPhrases.YES_MESSAGES,
					null,
					ConversationStates.ATTENDING,
					null,
					new ChangeLockAction());

				// refused offer to change locks 
				add(ConversationStates.SERVICE_OFFERED,
					ConversationPhrases.NO_MESSAGES,
					null,
					ConversationStates.ATTENDING,
					"OK, if you're really sure. Please let me know if I can help with anything else.",
					null);

				addJob("I'm an estate agent. In simple terms, I sell houses for the city of Kirdneh. Please ask about the #cost if you are interested. Our brochure is at #http://arianne.sourceforge.net/wiki/index.php?title=StendhalHouses.");
                addReply("reputation", "I will ask Hazel about you. Provided you've finished any task she asked you to do for her recently, and haven't left anything unfinished, she will like you.");
				addReply("buy",	"You may wish to know the #cost before you buy. Perhaps our brochure, #http://arianne.sourceforge.net/wiki/index.php?title=StendhalHouses would also be of interest.");
				addReply("really", "That's right, really, really, really. Really.");
				addOffer("I sell houses from this beautiful city of Kirdneh, please look at #http://arianne.sourceforge.net/wiki/index.php?title=StendhalHouses for examples of how they look inside. Then ask about the #cost when you are ready.");
				addHelp("You must have a good #reputation if you want to buy an available house in Kirdneh. If you can pay the #cost, I'll give you a key. As a house owner you can buy spare keys to give your friends. See #http://arianne.sourceforge.net/wiki/index.php?title=StendhalHouses for pictures inside typical houses and more details.");
				addQuest("You may buy houses from me, please ask the #cost if you are interested. Perhaps you would first like to view our brochure, #http://arianne.sourceforge.net/wiki/index.php?title=StendhalHouses.");
				addGoodbye("Goodbye.");
			}
		};

		npc3.setDescription("You see a smart looking man.");
		npc3.setEntityClass("man_004_npc");
		npc3.setPosition(31, 4);
		npc3.initHP(100);
		kirdneh_townhall_zone.add(npc3);
	}

	// we'd like to update houses sold before release of 0.73 with the owner name
	// when a player logs in we see if they own a house and we get the number from the house slot
	// this can be removed after all previously owned portals would have expired unless player haslogged in to pay tax 
	// as by then unclaimed houses will be recalimed by state
	// this will be 6? months after release of 0.73
	public void onLoggedIn(final Player player) {
		final String name = player.getName();
		if (player.hasQuest(QUEST_SLOT) && !"postman".equals(name)) {
			
			// note we default to a DIFFERENT value than the default house number incase neither found for some bad reasons
			final int id = MathHelper.parseIntDefault(player.getQuest(QUEST_SLOT), -1);
			logger.debug("Found that " + name + " owns house " + Integer.toString(id));
			// Now look for the house portal which matches this and update it to have the player name on it
			final List<HousePortal> portals =  HouseUtilities.getHousePortals();
			for (final HousePortal houseportal : portals) {
				final String owner = houseportal.getOwner();
				if ("an unknown owner".equals(owner)) {
					final int number = houseportal.getPortalNumber();
					if (number == id) {
						houseportal.setOwner(name);
						logger.debug(name + " owns house " + Integer.toString(id) + " and we labelled the house");
						return;
					}
				}
			}
		}
	}

	@Override
	public void addToWorld() {
		super.addToWorld();

		kirdneh_city_zone = SingletonRepository.getRPWorld().getZone(KIRDNEH_CITY);
		ados_city_zone = SingletonRepository.getRPWorld().getZone(ADOS_CITY);
		ados_city_n_zone = SingletonRepository.getRPWorld().getZone(ADOS_CITY_N);
		
		kalavan_city_zone = SingletonRepository.getRPWorld().getZone(KALAVAN_CITY);
		createNPC();

		ados_townhall_zone = SingletonRepository.getRPWorld().getZone(ADOS_TOWNHALL);
		createNPC2();

		kirdneh_townhall_zone = SingletonRepository.getRPWorld().getZone(KIRDNEH_TOWNHALL);
		createNPC3();

		SingletonRepository.getLoginNotifier().addListener(this);
		
		// Start collecting taxes as well
		houseTax = new HouseTax();
	}
}
