import java.util.*;
import java.util.stream.Collectors;

class Entry {
    Scanner in;
    int bustersPerPlayer; // the amount of busters you control
    int ghostCount; // the amount of ghosts on the map
    int myTeamId; // if this is 0, your base is on the top left of the map, if it is one, on the bottom right
    int entities; // the number of busters and ghosts visible to you
    List<Ghost> ghosts;
    List<Buster> myBusters;
    Buster hunter;
    Buster catcher;
    Buster support;
    List<Buster> enemyBusters;

    public Entry() {
        in = new Scanner(System.in);
        ghosts = new ArrayList<>();
        myBusters = new ArrayList<>();
        enemyBusters = new ArrayList<>();
    }

    public void getGameInfo() {
        bustersPerPlayer = in.nextInt();
        ghostCount = in.nextInt();
        myTeamId = in.nextInt();
    }

    public void getRoundInfo() {
        ghosts.clear();
        myBusters.clear();
        enemyBusters.clear();
        entities = in.nextInt();
        Character charact;
        for (int i = 0; i < entities; i++) {
            int entityId = in.nextInt(); // buster id or ghost id
            int x = in.nextInt();
            int y = in.nextInt(); // position of this buster / ghost
            int entityType = in.nextInt(); // the team id if it is a buster, -1 if it is a ghost.
            int entityRole = in.nextInt(); // -1 for ghosts, 0 for the HUNTER, 1 for the GHOST CATCHER and 2 for the SUPPORT
            int state = in.nextInt(); // For busters: 0=idle, 1=carrying a ghost. For ghosts: remaining stamina points.
            int value = in.nextInt(); // For busters: Ghost id being carried/busted or number of turns left when stunned. For ghosts: number of busters attempting to trap this ghost.
            try {
                charact = CharacterFactory.getCharacter(entityId, x, y, entityType, entityRole, state, value);
                if (charact.role.equals(Role.GHOST)) {
                    ghosts.add((Ghost)charact);
                } else if (entityType == myTeamId){
                    myBusters.add((Buster)charact);
                    if (charact.role.equals(Role.HUNTER)) hunter = (Buster)charact;
                    if (charact.role.equals(Role.CATCHER)) catcher = (Buster)charact;
                    if (charact.role.equals(Role.SUPPORT)) support = (Buster)charact;
                } else {
                    enemyBusters.add((Buster)charact);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}

class RandomHelper {
    int x;
    int y;

    public void getRandomPosition() {
        x = (int)(Math.random() * ((16000) + 1));
        y = (int)(Math.random() * ((9000) + 1));
    }
}

class Moves {
    String hunter, catcher, support, release;

    public Moves(String hunter, String catcher, String support, String release) {
        this.hunter = hunter;
        this.catcher = catcher;
        this.support = support;
        this.release = release;
    }

    public Moves(String hunter, String catcher, String support) {
        this.hunter = hunter;
        this.catcher = catcher;
        this.support = support;
    }

    public Moves() {
        
    }
}

class Ai {
    boolean radarUsed = false;
    int BUST_TRAP_LOWER_BOUND = 900;
    int BUST_TRAP_HIGHER_BOUND = 1760;
    int STUN_LIMIT = 1760;
    int baseBoundX;
    int baseBoundY;
    int roundNo;
    boolean hunterRdPosReached = true;
    boolean catcherRdPosReached = true;
    boolean supportRdPosReached = true;
    RandomHelper hunterRd;
    RandomHelper catcherRd;
    RandomHelper supportRd;
    Ghost toBust = null, toTrap = null, stolenToTrap;
    Buster toStun;

    Entry entry;
    Moves moves;


    public Ai(Entry entry) {
        this.entry = entry;
        roundNo = 0;
        moves = new Moves();
    }

    public void processRound() {
        System.err.println("hunter pos x: " + entry.hunter.x + ", y: " + entry.hunter.y);
        System.err.println("catcher pos x: " + entry.catcher.x + ", y: " + entry.catcher.y);
        System.err.println("support pos x: " + entry.support.x + ", y: " + entry.support.y);

        refreshRandomPositions();

        if (roundNo == 1) {
            // diriger les busters vers le milieu
            moves.hunter = "MOVE 8000 4500";
            moves.catcher = "MOVE 8000 4500";

            // si pas de ghost visible au spawn, soutien RADAR
            if (!isAnyGhostVisible()) {
                moves.support = "RADAR";
                radarUsed = true;
            } else {
                moves.support = "MOVE 8000 4500";
            }
        } else {

            // By default, all random moves but release
            moves.hunter = String.format("MOVE %s %s", hunterRd.x, hunterRd.y);
            moves.catcher = String.format("MOVE %s %s", catcherRd.x, catcherRd.y);
            if (radarUsed) moves.support = String.format("MOVE %s %s", supportRd.x, supportRd.y);
            else {
                moves.support = "RADAR";
                radarUsed = true;
            }

            // handle Release move
            if (entry.catcher.state.equals(BusterState.CARRIER)) {
                if (entry.myTeamId == 0 && Math.sqrt(Math.pow(entry.catcher.x, 2) + Math.pow(entry.catcher.y, 2)) < 1600.0) moves.release = "RELEASE";
                else if (entry.myTeamId == 1 && entry.catcher.x >= 15400 && entry.catcher.y >= 7400) moves.release = "RELEASE";
                else moves.release = String.format("MOVE %s %s", baseBoundX, baseBoundY);
            }

            // Si au moins un ghost est visible (busted ou pas),

            //      par default: tout le monde random
            //      2/ si 1 toBust, hunter = pursuit or bust best; catcher = pursuit hunter (ou release)
            //      3/ si 1 toTrap, catcher = pursuit best to trap (ou release)

            if (isAnyGhostVisible()) {
                toBust = chooseBestGhostToBust();
                toTrap = chooseBestGhostToTrap();
                // toStun = chooseBestBustersToStun();

                // Cas 3, si 0 busted/x, hunter = pursuit best; catcher = pursuit best (ou release)
                if (toBust != null) {
                    System.err.println("TO BUST : " + toBust.id + ", stamina: " + toBust.stamina);
                    System.err.println("ghost pos x: " + toBust.x + ", y: " + toBust.y);
                    System.err.println("hunter distance: " + Math.sqrt(Math.pow(toBust.x - entry.hunter.x, 2) + Math.pow(toBust.y - entry.hunter.y, 2)));
                    System.err.println("catcher distance: " + Math.sqrt(Math.pow(toBust.x - entry.catcher.x, 2) + Math.pow(toBust.y - entry.catcher.y, 2)));

                    manageHunter();
                }

                if (toTrap != null) {
                    System.err.println("TO TRAP is open : x=" + toTrap.x + ";y=" + toTrap.y);
                    // Catcher move toTrap (or release)
                    if (moves.release == null) { // catcher does not carry a ghost
                        // Catcher is in range
                        if (Math.sqrt(Math.pow(toTrap.x - entry.catcher.x, 2) + Math.pow(toTrap.y - entry.catcher.y, 2)) < BUST_TRAP_HIGHER_BOUND
                                && Math.sqrt(Math.pow(toTrap.x - entry.catcher.x, 2) + Math.pow(toTrap.y - entry.catcher.y, 2)) > BUST_TRAP_LOWER_BOUND) {
                            moves.catcher = String.format("TRAP %s ", toTrap.id);
                            toTrap = null;
                        }
                        // Catcher is not in range
                        else {
                            moves.hunter = String.format("MOVE %s %s", entry.hunter.x, entry.hunter.y);
                            moves.catcher = String.format("MOVE %s %s", toTrap.x, toTrap.y);
                        }
                    }
                }

//                if (toTrap == null) {
//                    if (entry.catcher.state.equals(BusterState.NOT_CARRIER)) moves.catcher  = String.format("MOVE %s %s", entry.hunter.x, entry.hunter.y);
//                }
            }
        }

        System.out.println(moves.hunter);
        System.out.println(moves.release == null ? moves.catcher : moves.release);
        System.out.println(moves.support);
        if (moves.release != null) moves.release = null;
    }

    // Pursuit or bust best
    private void manageHunter() {

        // Hunter is in range
        if (Math.sqrt(Math.pow(toBust.x - entry.hunter.x, 2) + Math.pow(toBust.y - entry.hunter.y, 2)) < BUST_TRAP_HIGHER_BOUND
                && Math.sqrt(Math.pow(toBust.x - entry.hunter.x, 2) + Math.pow(toBust.y - entry.hunter.y, 2)) > BUST_TRAP_LOWER_BOUND) {
            System.err.println("accepted distance: " + Math.sqrt(Math.pow(toBust.x - entry.hunter.x, 2) + Math.pow(toBust.y - entry.hunter.y, 2)));
            // Hunter bust
            moves.hunter = String.format("BUST %s ", toBust.id);
            if (entry.catcher.state.equals(BusterState.NOT_CARRIER)) moves.catcher  = String.format("MOVE %s %s", entry.hunter.x, entry.hunter.y);
        }

        // Hunter is not in range
        else {
            // Hunter pursuit the best to bust
            moves.hunter = String.format("MOVE %s %s", toBust.x, toBust.y);
            // Catcher release or move to the ghost toBust
            if (entry.catcher.state.equals(BusterState.NOT_CARRIER)) moves.catcher  = String.format("MOVE %s %s", toBust.x, toBust.y);
        }

    }

    private void refreshRandomPositions() {
        // refresh hunter random position
        if (!hunterRdPosReached) {
            if (entry.hunter.x == hunterRd.x && entry.hunter.y == hunterRd.y) {
                hunterRdPosReached = true;
            }
        } else {
            hunterRd = new RandomHelper();
            hunterRd.getRandomPosition();
            System.err.println("New hunter random pos: (x=" + hunterRd.x + ";y=" + hunterRd.y + ")");
            hunterRdPosReached = false;
        }

        // refresh catcher random position
        if (!catcherRdPosReached) {
            if (entry.catcher.x == catcherRd.x && entry.catcher.y == catcherRd.y) {
                catcherRdPosReached = true;
            }
        } else {
            catcherRd = new RandomHelper();
            catcherRd.getRandomPosition();
            System.err.println("New catcher random pos: (x=" + catcherRd.x + ";y=" + catcherRd.y + ")");
            catcherRdPosReached = false;
        }

        // refresh support random position
        if (!supportRdPosReached) {
            if (entry.support.x == supportRd.x && entry.support.y == supportRd.y) {
                supportRdPosReached = true;
            }
        } else {
            supportRd = new RandomHelper();
            supportRd.getRandomPosition();
            System.err.println("New support random pos: (x=" + supportRd.x + ";y=" + supportRd.y + ")");
            supportRdPosReached = false;
        }
    }

    public boolean isAnyGhostVisible() {
        return !entry.ghosts.isEmpty();
    }

    public boolean isVisibleBuster() {
        return !entry.ghosts.stream().filter(ghost -> ghost.stamina != 0).collect(Collectors.toList()).isEmpty();
    }

    public Ghost chooseBestGhostToBust() {
        int minStamina = 50;
        Ghost best = null;
        for (Ghost ghost : entry.ghosts) {
            if (ghost.stamina < minStamina && ghost.stamina != 0) {
                minStamina = ghost.stamina;
                best = ghost;
            }
        }
        if (best == null) System.err.println("chooseBest returned null");
        return best;
    }

    private Ghost chooseBestGhostToTrap() {
        double minDistance = 2200.0;
        Ghost bestToTrap = null;
        List<Ghost> busted = entry.ghosts.stream().filter(ghost -> ghost.stamina == 0).collect(Collectors.toList());
        for (Ghost ghost : busted) {
            double distance = Math.sqrt(Math.pow(ghost.x - entry.catcher.x, 2) + Math.pow(ghost.y - entry.catcher.y, 2));
            if (distance < minDistance) {
                bestToTrap = ghost;
                minDistance = distance;
            }
        }
        return bestToTrap;
    }

//    private Buster chooseBestBustersToStun() {
//        Buster bestToStun, secondBestToStun;
//        for (Buster enemy : entry.enemyBusters) {
//            switch (enemy.role) {
//                case HUNTER:
//                    if (enemy.state.equals(BusterState.BUSTING)) bestToStun = enemy;
//                    break;
//                case CATCHER:
//                    if (enemy.state.equals(BusterState.CARRIER)) {
//                        // stolenToTrap =
//                        return enemy;
//                    }
//                    if (enemy.state.equals(BusterState.TRAPPING)) secondBestToStun = enemy;
//                    break;
//                case SUPPORT:
//
//                    break;
//            }
//        }
//    }

    public void setBaseBounds() {
        if (entry.myTeamId == 0) {
            baseBoundX = 0;
            baseBoundY = 0;
        } else {
            baseBoundX = 16000;
            baseBoundY = 9000;
        }
    }

    public void refreshEntry(Entry entry) {
        this.entry = entry;
        roundNo++;
    }
}

/**
 * Send your busters out into the fog to trap ghosts and bring them home!
 **/
class Player {

    public static void main(String args[]) {
        Entry entry = new Entry();
        entry.getGameInfo();

        Ai analyser = new Ai(entry);
        analyser.setBaseBounds();

        // game loop
        while (true) {
            entry.getRoundInfo();

            // Write an action using System.out.println()
            // To debug: System.err.println("Debug messages...");
            analyser.refreshEntry(entry);
            analyser.processRound();

            // First the HUNTER : MOVE x y | BUST id
            // Second the GHOST CATCHER: MOVE x y | TRAP id | RELEASE
            // Third the SUPPORT: MOVE x y | STUN id | RADAR
//            System.out.println("MOVE 8000 4500");
//            System.out.println("MOVE 8000 4500");
//            System.out.println("MOVE 8000 4500");
        }
    }
}

class CharacterFactory {
    public static Character getCharacter(int id, int x, int y, int type, int role, int state, int value) throws Exception {
        switch (role) {
            case -1:
                return new Ghost(id, x, y, type, Role.valueOf(role), state, value);
            default:
                return new Buster(id, x, y, type, Role.valueOf(role), state, value);
        }
    }
}

class Character {
    int id;
    int x;
    int y;
    int type;
    Role role;
    int value;

    public Character(int id, int x, int y, int type, Role role, int value) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.type = type;
        this.role = role;
        this.value = value;
    }
}

class Buster extends Character {
    BusterState state;

    public Buster(int id, int x, int y, int type, Role role, int state, int value) {
        super(id, x, y, type, role, value);
        try {
            this.state = BusterState.valueOf(state);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class Ghost extends Character {
    int stamina;

    public Ghost(int id, int x, int y, int type, Role role, int state, int value) {
        super(id, x, y, type, role, value);
        this.stamina = state;
    }
}

enum Role {
    HUNTER(0), CATCHER(1), SUPPORT(2), GHOST(-1);

    int id;
    Role(int id) {
        this.id = id;
    }

    public static Role valueOf(int id) throws Exception {
        switch (id) {
            case 0:
                return Role.HUNTER;
            case 1:
                return Role.CATCHER;
            case 2:
                return Role.SUPPORT;
            case -1:
                return Role.GHOST;
            default:
                throw new Exception("Unknown role id " + id);
        }
    }
}

enum BusterState {
    NOT_CARRIER(0), CARRIER(1), STUNNED(2), TRAPPING(3), BUSTING(4);

    int id;
    BusterState(int id) {
        this.id = id;
    }

    public static BusterState valueOf(int state) throws Exception {
        switch (state) {
            case 0:
                return BusterState.NOT_CARRIER;
            case 1:
                return BusterState.CARRIER;
            case 2:
                return BusterState.STUNNED;
            case 3:
                return BusterState.TRAPPING;
            case 4:
                return BusterState.BUSTING;
            default:
                throw new Exception("Unknown buster state id " + state);
        }
    }
}
