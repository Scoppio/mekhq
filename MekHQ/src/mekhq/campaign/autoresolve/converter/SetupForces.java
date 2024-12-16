/*
 * Copyright (c) 2024 - The MegaMek Team. All Rights Reserved.
 *
 *  This file is part of MekHQ.
 *
 *  MekHQ is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  MekHQ is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with MekHQ. If not, see <http://www.gnu.org/licenses/>.
 */

package mekhq.campaign.autoresolve.converter;

import io.sentry.Sentry;
import megamek.common.*;
import megamek.common.alphaStrike.conversion.ASConverter;
import megamek.common.force.Force;
import megamek.common.force.Forces;
import megamek.common.icons.Camouflage;
import megamek.common.options.OptionsConstants;
import megamek.common.planetaryconditions.PlanetaryConditions;
import megamek.logging.MMLogger;
import mekhq.campaign.Campaign;
import mekhq.campaign.autoresolve.acar.SimulationContext;
import mekhq.campaign.copy.CrewRefBreak;
import mekhq.campaign.mission.AtBDynamicScenario;
import mekhq.campaign.mission.AtBScenario;
import mekhq.campaign.mission.BotForce;
import mekhq.campaign.mission.Scenario;
import mekhq.campaign.unit.Unit;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Luana Coppio
 */
public class SetupForces {
    private static final MMLogger logger = MMLogger.create(SetupForces.class);

    private final Campaign campaign;
    private final List<Unit> units;
    private final AtBScenario scenario;

    /**
    * BalancedConsolidateForces is a helper class that redistribute entities and forces
    * in a way to consolidate then into valid forces to build Formations out of them.
    * @author Luana Coppio
    */
    public static class BalancedConsolidateForces {

        public static final int MAX_ENTITIES_IN_SUB_FORCE = 6;
        public static final int MAX_ENTITIES_IN_TOP_LEVEL_FORCE = 20;

        public record Container(int uid, int teamId, int[] entities, Container[] subs) {
            public boolean isLeaf() {
                return subs.length == 0 && entities.length > 0;
            }

            public boolean isTop() {
                return subs.length > 0 && entities.length == 0;
            }

            public String toString() {
                return "Container(uid=" + uid + ", team=" + teamId + ", ent=" + Arrays.toString(entities) + ", subs=" + Arrays.toString(subs) + ")";
            }
        }

        public record ForceRepresentation(int uid, int teamId, int[] entities, int[] subForces) {
            public boolean isLeaf() {
                return subForces.length == 0 && entities.length > 0;
            }

            public boolean isTop() {
                return subForces.length > 0 && entities.length == 0;
            }
        }

        /**
         * Balances the forces by team, tries to ensure that every team has the same number of top level forces, each within the ACS parameters
         * of a maximum of 20 entities and 4 sub forces. It also aggregates the entities by team instead of keeping segregated by player.
         * See the test cases for examples on how it works.
         * @param forces List of Forces to balance
         * @return List of Trees representing the balanced forces
         */
        public static List<Container> balancedLists(List<ForceRepresentation> forces) {
            Map<Integer, Set<Integer>> entitiesByTeam = new HashMap<>();
            for (ForceRepresentation c : forces) {
                entitiesByTeam.computeIfAbsent(c.teamId(), k -> new HashSet<>()).addAll(Arrays.stream(c.entities()).boxed().toList());
            }

            // Find the number of top-level containers for each team
            Map<Integer, Integer> numOfEntitiesByTeam = entitiesByTeam.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size()));

            int maxEntities = numOfEntitiesByTeam.values().stream().max(Integer::compareTo).orElse(0);
            int topCount = (int) Math.ceil((double) maxEntities / MAX_ENTITIES_IN_TOP_LEVEL_FORCE);

            Map<Integer, Container> balancedForces = new HashMap<>();

            for (int team : entitiesByTeam.keySet()) {
                createTopLevelForTeam(balancedForces, team, new ArrayList<>(entitiesByTeam.get(team)), topCount);
            }

            return new ArrayList<>(balancedForces.values());
        }

        private static void createTopLevelForTeam(Map<Integer, Container> cmap, int team, List<Integer> allEnt, int topCount) {
            int maxId = cmap.keySet().stream().max(Integer::compareTo).orElse(0) + 1;

            int perTop = (int) Math.min(Math.ceil((double) allEnt.size() / topCount), MAX_ENTITIES_IN_TOP_LEVEL_FORCE);

            int idx = 0;

            for (int i = 0; i < topCount; i++) {
                int end = Math.min(idx + perTop, allEnt.size());
                List<Integer> part = allEnt.subList(idx, end);
                idx = end;
                // split part into sub containers of up to 6 entities
                List<Container> subs = new ArrayList<>();
                int step = Math.min(part.size(), MAX_ENTITIES_IN_SUB_FORCE);
                for (int start = 0; start < part.size(); start += step) {
                    var subForceSize = Math.min(part.size(), start + step);
                    Container leaf = new Container(
                        maxId++,
                        team,
                        part.subList(start, subForceSize).stream().mapToInt(Integer::intValue).toArray(),
                        new Container[0]);
                    subs.add(leaf);
                }

                if (subs.isEmpty()) {
                    // no entities? skip creating top-level
                    break;
                }

                var containers = new Container[subs.size()];
                for (int k = 0; k < containers.length; k++) {
                    containers[k] = subs.get(k);
                }

                Container top = new Container(maxId++, team, new int[0], containers);
                cmap.put(top.uid(), top);
            }
        }

        public static boolean isBalanced(List<BalancedConsolidateForces.Container> postBalanceForces) {
            if (postBalanceForces.isEmpty()) {
                return false;
            }
            Map<Integer, List<BalancedConsolidateForces.Container>> resMap = new HashMap<>();
            for (BalancedConsolidateForces.Container c : postBalanceForces) {
                if (c.isTop()) resMap.computeIfAbsent(c.teamId(), k -> new ArrayList<>()).add(c);
            }

            List<Integer> counts = resMap.values().stream().map(List::size).toList();
            int min = Collections.min(counts), max = Collections.max(counts);
            return max - min <= 1;
        }
    }

    private static class ConsolidateForces {

        /**
         * Consolidates forces by redistributing entities and sub forces as needed.
         * It will balance the forces by team, ensuring that each force has a maximum of 20 entities and 4 sub forces.
         * @param game The game to consolidate forces for
         */
        public static void consolidateForces(IGame game) {
            Forces forces = game.getForces();
            var teamByPlayer = game.getTeamByPlayer();
            var forceNameByPlayer = new HashMap<Integer, String>();
            for (var force : forces.getAllForces()) {
                if (!forceNameByPlayer.containsKey(force.getOwnerId())) {
                    forceNameByPlayer.put(force.getOwnerId(), force.getName());
                }
            }
            var representativeOwnerForForce = new HashMap<Integer, List<Player>>();
            for (var force : forces.getAllForces()) {
                representativeOwnerForForce.computeIfAbsent(teamByPlayer.get(force.getOwnerId()), k -> new ArrayList<>()).add(game.getPlayer(force.getOwnerId()));
            }

            var forceRepresentation = getForceRepresentations(forces, teamByPlayer);
            var balancedConsolidateForces = BalancedConsolidateForces.balancedLists(forceRepresentation);

            clearAllForces(forces);

            for (var forceRep : balancedConsolidateForces) {
                var player = representativeOwnerForForce.get(forceRep.teamId()).get(0);
                var parentForceId = forces.addTopLevelForce(
                    new Force(
                        "[Team " + forceRep.teamId()  + "] "+ forceNameByPlayer.get(player.getId()) + " Formation",
                        -1,
                        new Camouflage(),
                        player),
                    player);
                for (var subForce : forceRep.subs()) {
                    var subForceId = forces.addSubForce(
                        new Force(
                            "[Team " + forceRep.teamId()  + "] " + subForce.uid() + " Unit",
                            -1,
                            new Camouflage(),
                            player),
                        forces.getForce(parentForceId));
                    for (var entityId : subForce.entities()) {
                        forces.addEntity((Entity) game.getEntityFromAllSources(entityId), subForceId);
                    }
                }
            }
        }

        private static void clearAllForces(Forces forces) {
            // Remove all empty forces and sub forces after consolidation
            forces.deleteForces(forces.getAllForces());

        }

        /**
         * Converts the forces into a list of ForceRepresentations. It is an intermediary representation of a force, in a way that makes it very
         * lightweight to manipulate and balance. It only contains the representation of the force top-level, and the list of entities in it.
         * @param forces The forces to convert
         * @param teamByPlayer A map of player IDs to team IDs
         * @return A list of ForceRepresentations
         */
        private static List<BalancedConsolidateForces.ForceRepresentation> getForceRepresentations(Forces forces, Map<Integer, Integer> teamByPlayer) {
            List<BalancedConsolidateForces.ForceRepresentation> forceRepresentations = new ArrayList<>();
            for (Force force : forces.getTopLevelForces()) {
                int[] entityIds = forces.getFullEntities(force).stream().mapToInt(ForceAssignable::getId).toArray();
                forceRepresentations.add(new BalancedConsolidateForces.ForceRepresentation(force.getId(), teamByPlayer.get(force.getOwnerId()), entityIds, new int[0]));
            }
            return forceRepresentations;
        }

    }

    public SetupForces(Campaign campaign, List<Unit> units, AtBScenario scenario) {
        this.campaign = campaign;
        this.units = units;
        this.scenario = scenario;
    }

    /**
     * Create the forces for the game object, using the campaign, units and scenario
     * @param game The game object to setup the forces in
     */
    public void createForcesOnGame(SimulationContext game) {
        setupPlayer(game);
        setupBots(game);
        ConsolidateForces.consolidateForces(game);
        convertForcesIntoFormations(game);
    }

    private static class FailedToConvertForceToFormationException extends RuntimeException {
        public FailedToConvertForceToFormationException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * Convert the forces in the game to formations, this is the most important step in the setup of the game,
     * it converts every top level force into a single formation, and those formations are then added to the game
     * and used in the auto resolve in place of the original entities
     * @param game The game object to convert the forces in
     */
    private static void convertForcesIntoFormations(SimulationContext game) {
        for(var force : game.getForces().getTopLevelForces()) {
            try {
                var formation = new ForceToFormationConverter(force, game).convert();
                formation.setTargetFormationId(Entity.NONE);
                game.addUnit(formation);
                game.getForces().addEntity(formation, force.getId());
            } catch (Exception e) {
                Sentry.captureException(e);
                var entities = game.getForces().getFullEntities(force).stream().filter(Entity.class::isInstance)
                    .map(Entity.class::cast).toList();
                logger.error("Error converting force to formation {} - {}", force, entities, e);
                throw new FailedToConvertForceToFormationException(e);
            }
        }
    }

    /**
     * Setup the player, its forces and entities in the game, it also sets the player skill level.
     * @param game The game object to setup the player in
     */
    private void setupPlayer(SimulationContext game) {
        var player = getCleanPlayer();
        game.addPlayer(player.getId(), player);
        var entities = setupPlayerForces(player);
        var playerSkill = campaign.getReputation().getAverageSkillLevel();
        game.setPlayerSkillLevel(player.getId(), playerSkill);
        sendEntities(entities, game);
    }

    /**
     * Setup the bots, their forces and entities in the game, it also sets the player skill level.
     * @param game The game object to setup the bots in
     */
    private void setupBots(SimulationContext game) {
        var enemySkill = (scenario.getContract(campaign)).getEnemySkill();
        var allySkill = (scenario.getContract(campaign)).getAllySkill();
        var localBots = new HashMap<String, Player>();
        for (int i = 0; i < scenario.getNumBots(); i++) {
            BotForce bf = scenario.getBotForce(i);
            String name = bf.getName();
            if (localBots.containsKey(name)) {
                int append = 2;
                while (localBots.containsKey(name + append)) {
                    append++;
                }
                name += append;
            }
            var highestPlayerId = game.getPlayersList().stream().mapToInt(Player::getId).max().orElse(0);
            Player bot = new Player(highestPlayerId + 1, name);
            bot.setTeam(bf.getTeam());
            localBots.put(name, bot);
            configureBot(bot, bf);
            game.addPlayer(bot.getId(), bot);
            if (bot.isEnemyOf(campaign.getPlayer())) {
                game.setPlayerSkillLevel(bot.getId(), enemySkill);
            } else {
                game.setPlayerSkillLevel(bot.getId(), allySkill);
            }
            bf.generateRandomForces(units, campaign);
            var entities = bf.getFullEntityList(campaign);
            var botEntities = setupBotEntities(bot, entities, bf.getDeployRound());
            sendEntities(botEntities, game);
        }
    }

    /**
     * Create a player object from the campaign and scenario wichi doesnt have a reference to the original player
     * @return The clean player object
     */
    private Player getCleanPlayer() {
        var campaignPlayer = campaign.getPlayer();
        var player = new Player(campaignPlayer.getId(), campaignPlayer.getName());
        player.setCamouflage(campaign.getCamouflage().clone());
        player.setColour(campaign.getColour());
        player.setStartingPos(scenario.getStartingPos());
        player.setStartOffset(scenario.getStartOffset());
        player.setStartWidth(scenario.getStartWidth());
        player.setStartingAnyNWx(scenario.getStartingAnyNWx());
        player.setStartingAnyNWy(scenario.getStartingAnyNWy());
        player.setStartingAnySEx(scenario.getStartingAnySEx());
        player.setStartingAnySEy(scenario.getStartingAnySEy());
        player.setTeam(1);
        player.setNbrMFActive(scenario.getNumPlayerMinefields(Minefield.TYPE_ACTIVE));
        player.setNbrMFConventional(scenario.getNumPlayerMinefields(Minefield.TYPE_CONVENTIONAL));
        player.setNbrMFInferno(scenario.getNumPlayerMinefields(Minefield.TYPE_INFERNO));
        player.setNbrMFVibra(scenario.getNumPlayerMinefields(Minefield.TYPE_VIBRABOMB));
        player.getTurnInitBonus();
        return player;
    }

    /**
     * Setup the player forces and entities for the game
     * @param player The player object to setup the forces for
     * @return A list of entities for the player
     */
    private List<Entity> setupPlayerForces(Player player) {
        boolean useDropship = false;
        if (scenario.getLanceRole().isScouting()) {
            for (Entity en : scenario.getAlliesPlayer()) {
                if (en.getUnitType() == UnitType.DROPSHIP) {
                    useDropship = true;
                    break;
                }
            }
            if (!useDropship) {
                for (Unit unit : units) {
                    if (unit.getEntity().getUnitType() == UnitType.DROPSHIP) {
                        useDropship = true;
                        break;
                    }
                }
            }
        }
        var entities = new ArrayList<Entity>();

        for (Unit unit : units) {
            // Get the Entity
            var entity = ASConverter.getUndamagedEntity(unit.getEntity());
            // Set the TempID for auto reporting
            if (Objects.isNull(entity)) {
                continue;
            }

            entity.setExternalIdAsString(unit.getId().toString());
            // Set the owner
            entity.setOwner(player);

            // If this unit is a spacecraft, set the crew size and marine size values
            if (entity.isLargeCraft() || (entity.getUnitType() == UnitType.SMALL_CRAFT)) {
                entity.setNCrew(unit.getActiveCrew().size());
                entity.setNMarines(unit.getMarineCount());
            }
            // Calculate deployment round
            int deploymentRound = entity.getDeployRound();
            if (!(scenario instanceof AtBDynamicScenario)) {
                int speed = entity.getWalkMP();
                if (entity.getJumpMP() > 0) {
                    if (entity instanceof Infantry) {
                        speed = entity.getJumpMP();
                    } else {
                        speed++;
                    }
                }
                // Set scenario type-specific delay
                deploymentRound = Math.max(entity.getDeployRound(), scenario.getDeploymentDelay() - speed);
                // Lances deployed in scout roles always deploy units in 6-walking speed turns
                if (scenario.getLanceRole().isScouting() && (scenario.getStrategicFormation(campaign) != null)
                    && (scenario.getStrategicFormation(campaign).getForceId() == scenario.getStrategicFormationId())
                    && !useDropship) {
                    deploymentRound = Math.max(deploymentRound, 6 - speed);
                }
            }
            entity.setDeployRound(deploymentRound);
            var force = campaign.getForceFor(unit);
            if (force != null) {
                entity.setForceString(force.getFullMMName());
            } else if (!unit.getEntity().getForceString().isBlank()) {
                // this was added mostly to make it easier to run tests
                entity.setForceString(unit.getEntity().getForceString());
            }
            var newCrewRef = new CrewRefBreak(unit.getEntity().getCrew()).copy();
            entity.setCrew(newCrewRef);
            entities.add(entity);
        }

        for (Entity entity : scenario.getAlliesPlayer()) {
            if (null == entity) {
                continue;
            }
            entity.setOwner(player);

            int deploymentRound = entity.getDeployRound();
            if (!(scenario instanceof AtBDynamicScenario)) {
                int speed = entity.getWalkMP();
                if (entity.getJumpMP() > 0) {
                    if (entity instanceof Infantry) {
                        speed = entity.getJumpMP();
                    } else {
                        speed++;
                    }
                }
                deploymentRound = Math.max(entity.getDeployRound(), scenario.getDeploymentDelay() - speed);
                if (!useDropship && scenario.getLanceRole().isScouting()
                    && (scenario.getStrategicFormation(campaign).getForceId() == scenario.getStrategicFormationId())) {
                    deploymentRound = Math.max(deploymentRound, 6 - speed);
                }
            }

            entity.setDeployRound(deploymentRound);
            entities.add(entity);
        }

        return entities;
    }

    /**
     * Setup the map settings for the game, not relevant at the moment, as the map settings are not used in the autoresolve currently
     * @return The map settings object
     */
    private MapSettings setupMapSettings() {
        MapSettings mapSettings = MapSettings.getInstance();
        mapSettings.setBoardSize(scenario.getMapX(), scenario.getMapY());
        mapSettings.setMapSize(1, 1);
        mapSettings.getBoardsSelectedVector().clear();

        // if the scenario is taking place in space, do space settings instead
        if (scenario.getBoardType() == Scenario.T_SPACE
            || scenario.getTerrainType().equals("Space")) {
            mapSettings.setMedium(MapSettings.MEDIUM_SPACE);
            mapSettings.getBoardsSelectedVector().add(MapSettings.BOARD_GENERATED);
        } else if (scenario.isUsingFixedMap()) {
            String board = scenario.getMap().replace(".board", "");
            board = board.replace("\\", "/");
            mapSettings.getBoardsSelectedVector().add(board);

            if (scenario.getBoardType() == Scenario.T_ATMOSPHERE) {
                mapSettings.setMedium(MapSettings.MEDIUM_ATMOSPHERE);
            }
        } else {
            File mapgenFile = new File("data/mapgen/" + scenario.getMap() + ".xml");
            try (InputStream is = new FileInputStream(mapgenFile)) {
                mapSettings = MapSettings.getInstance(is);
            } catch (IOException ex) {
                Sentry.captureException(ex);
                logger.error(
                    String.format("Could not load map file data/mapgen/%s.xml", scenario.getMap()),
                    ex);
            }

            if (scenario.getBoardType() == Scenario.T_ATMOSPHERE) {
                mapSettings.setMedium(MapSettings.MEDIUM_ATMOSPHERE);
            }

            // duplicate code, but getting a new instance of map settings resets the size
            // parameters
            mapSettings.setBoardSize(scenario.getMapX(), scenario.getMapY());
            mapSettings.setMapSize(1, 1);
            mapSettings.getBoardsSelectedVector().add(MapSettings.BOARD_GENERATED);
        }
        return mapSettings;
    }

    /**
     * Configure the bot player object with the bot force data
     * @param bot The bot player object
     * @param botForce The bot force data
     */
    private void configureBot(Player bot, BotForce botForce) {
        bot.setTeam(botForce.getTeam());
        // set deployment
        bot.setStartingPos(botForce.getStartingPos());
        bot.setStartOffset(botForce.getStartOffset());
        bot.setStartWidth(botForce.getStartWidth());
        bot.setStartingAnyNWx(botForce.getStartingAnyNWx());
        bot.setStartingAnyNWy(botForce.getStartingAnyNWy());
        bot.setStartingAnySEx(botForce.getStartingAnySEx());
        bot.setStartingAnySEy(botForce.getStartingAnySEy());

        // set camo
        bot.setCamouflage(botForce.getCamouflage().clone());
        bot.setColour(botForce.getColour());
    }

    /**
     * Setup the bot entities for the game
     * @param bot The bot player object
     * @param originalEntities The original entities for the bot
     * @param deployRound The deployment round for the bot
     * @return A list of entities for the bot
     */
    private List<Entity> setupBotEntities(Player bot, List<Entity> originalEntities, int deployRound) {
        String forceName = bot.getName() + "|1";
        var entities = new ArrayList<Entity>();

        for (Entity originalBotEntity : originalEntities) {
            var entity = ASConverter.getUndamagedEntity(originalBotEntity);
            if (entity == null) {
                logger.warn("Could not convert entity for bot {} - {}", bot.getName(), originalBotEntity);
                continue;
            }

            entity.setOwner(bot);
            entity.setForceString(forceName);
            entity.setCrew(getNewCrewRef(entity.getCrew()));
            entity.setId(originalBotEntity.getId());
            entity.setExternalIdAsString(originalBotEntity.getExternalIdAsString());
            entity.setCommander(originalBotEntity.isCommander());

            if (entity.getDeployRound() == 0) {
                entity.setDeployRound(deployRound);
            }
            entities.add(entity);
        }
        return entities;
    }

    /**
     * Get the planetary conditions for the game, not used at the moment in the auto resolve
     * @return The planetary conditions object
     */
    private PlanetaryConditions getPlanetaryConditions() {
        PlanetaryConditions planetaryConditions = new PlanetaryConditions();
        if (campaign.getCampaignOptions().isUseLightConditions()) {
            planetaryConditions.setLight(scenario.getLight());
        }
        if (campaign.getCampaignOptions().isUseWeatherConditions()) {
            planetaryConditions.setWeather(scenario.getWeather());
            planetaryConditions.setWind(scenario.getWind());
            planetaryConditions.setFog(scenario.getFog());
            planetaryConditions.setEMI(scenario.getEMI());
            planetaryConditions.setBlowingSand(scenario.getBlowingSand());
            planetaryConditions.setTemperature(scenario.getModifiedTemperature());
        }
        if (campaign.getCampaignOptions().isUsePlanetaryConditions()) {
            planetaryConditions.setAtmosphere(scenario.getAtmosphere());
            planetaryConditions.setGravity(scenario.getGravity());
        }
        return planetaryConditions;
    }

    /**
     * Send the entities to the game object
     * @param entities The entities to send
     * @param game the game object to send the entities to
     */
    private void sendEntities(List<Entity> entities, SimulationContext game) {
        Map<Integer, Integer> forceMapping = new HashMap<>();
        for (final Entity entity : new ArrayList<>(entities)) {
            if (entity instanceof ProtoMek) {
                int numPlayerProtos = game.getSelectedEntityCount(new EntitySelector() {
                    private final int ownerId = entity.getOwnerId();
                    @Override
                    public boolean accept(Entity entity) {
                        return (entity instanceof ProtoMek) && (ownerId == entity.getOwnerId());
                    }
                });

                entity.setUnitNumber((short) (numPlayerProtos / 5));
            }

            if (Entity.NONE == entity.getId()) {
                entity.setId(game.getNextEntityId());
            }

            // Give the unit a spotlight, if it has the spotlight quirk
            entity.setExternalSearchlight(entity.hasExternalSearchlight()
                || entity.hasQuirk(OptionsConstants.QUIRK_POS_SEARCHLIGHT));

            game.getPlayer(entity.getOwnerId()).changeInitialEntityCount(1);
            game.getPlayer(entity.getOwnerId()).changeInitialBV(entity.calculateBattleValue());

            // Restore forces from MULs or other external sources from the forceString, if
            // any
            if (!entity.getForceString().isBlank()) {
                List<megamek.common.force.Force> forceList = Forces.parseForceString(entity);
                int realId = megamek.common.force.Force.NO_FORCE;
                boolean topLevel = true;

                for (megamek.common.force.Force force : forceList) {
                    if (!forceMapping.containsKey(force.getId())) {
                        if (topLevel) {
                            realId = game.getForces().addTopLevelForce(force, entity.getOwner());
                        } else {
                            megamek.common.force.Force parent = game.getForces().getForce(realId);
                            realId = game.getForces().addSubForce(force, parent);
                        }
                        forceMapping.put(force.getId(), realId);
                    } else {
                        realId = forceMapping.get(force.getId());
                    }
                    topLevel = false;
                }
                entity.setForceString("");

                game.addEntity(entity);
                game.getForces().addEntity(entity, realId);
            }
        }
    }
}