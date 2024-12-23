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

package mekhq.campaign.autoresolve.acar.phase;
import megamek.ai.utility.Memory;
import megamek.common.Compute;
import megamek.common.IEntityRemovalConditions;
import megamek.common.enums.GamePhase;
import megamek.common.strategicBattleSystems.SBFUnit;
import megamek.common.util.weightedMaps.WeightedDoubleMap;
import mekhq.campaign.autoresolve.acar.SimulationManager;
import mekhq.campaign.autoresolve.acar.action.MoraleCheckAction;
import mekhq.campaign.autoresolve.acar.action.RecoveringNerveAction;
import mekhq.campaign.autoresolve.acar.action.WithdrawAction;
import mekhq.campaign.autoresolve.acar.report.EndPhaseReporter;
import mekhq.campaign.autoresolve.component.Formation;
import mekhq.campaign.unit.damage.DamageApplierChooser;


import java.util.List;

public class EndPhase extends PhaseHandler {

    private final EndPhaseReporter reporter;

    public EndPhase(SimulationManager simulationManager) {
        super(simulationManager, GamePhase.END);
        reporter = new EndPhaseReporter(simulationManager.getGame(), simulationManager::addReport);
    }

    @Override
    protected void executePhase() {
        reporter.endPhaseHeader();
        checkUnitDestruction();
        checkMorale();
        checkWithdrawingForces();
        checkRecoveringNerves();
        forgetEverything();
    }

    private void checkUnitDestruction() {
        var areThereUnitsToDestroy = getContext().getActiveFormations().stream()
            .flatMap(f -> f.getUnits().stream()).anyMatch(u -> u.getCurrentArmor() <= 0);
        if (areThereUnitsToDestroy) {
            reporter.destroyedUnitsHeader();
        }
        var allFormations = getContext().getActiveFormations();
        for (var formation : allFormations) {
            var destroyedUnits = formation.getUnits().stream()
                .filter(u -> u.getCurrentArmor() <= 0)
                .toList();
            if (!destroyedUnits.isEmpty()) {
                destroyUnits(formation, destroyedUnits);
            }
        }
    }

    private void checkWithdrawingForces() {
        if (getSimulationManager().isVictory()) {
            // If the game is over, no need to withdraw
            return;
        }
        var forcedWithdrawingUnits = getSimulationManager().getGame().getActiveFormations().stream()
            .filter(f -> f.moraleStatus() == Formation.MoraleStatus.ROUTED || f.isCrippled())
            .toList();

        for (var formation : forcedWithdrawingUnits) {
            getSimulationManager().addWithdraw(new WithdrawAction(formation.getId()));
        }
    }

    private void checkMorale() {
        var formationNeedsMoraleCheck = getSimulationManager().getGame().getActiveFormations().stream()
            .filter(Formation::hadHighStressEpisode)
            .toList();

        for (var formation : formationNeedsMoraleCheck) {
            getSimulationManager().addMoraleCheck(new MoraleCheckAction(formation.getId()), formation);
        }
    }

    private void checkRecoveringNerves() {
        var recoveringNerves = getSimulationManager().getGame().getActiveFormations().stream()
            .filter(f -> f.moraleStatus().ordinal() > Formation.MoraleStatus.NORMAL.ordinal())
            .toList();

        for (var formation : recoveringNerves) {
            getSimulationManager().addNerveRecovery(new RecoveringNerveAction(formation.getId()));
        }
    }

    private void forgetEverything() {
        var formations = getSimulationManager().getGame().getActiveFormations();
        formations.stream().map(Formation::getMemory).forEach(Memory::clear);
        formations.forEach(Formation::reset);
    }

    private static final WeightedDoubleMap<Integer> REMOVAL_CONDITIONS_TABLE = WeightedDoubleMap.of(
        IEntityRemovalConditions.REMOVE_SALVAGEABLE, 2,
        IEntityRemovalConditions.REMOVE_DEVASTATED, 5,
        IEntityRemovalConditions.REMOVE_EJECTED, 10
    );

    private static final WeightedDoubleMap<Integer> REMOVAL_CONDITIONS_TABLE_NO_EJECTION = WeightedDoubleMap.of(
        IEntityRemovalConditions.REMOVE_SALVAGEABLE, 3,
        IEntityRemovalConditions.REMOVE_DEVASTATED, 7
    );

    public void destroyUnits(Formation formation, List<SBFUnit> destroyedUnits) {
        for (var unit : destroyedUnits) {
            for (var element : unit.getElements()) {
                var entityOpt = getContext().getEntity(element.getId());
                if (entityOpt.isPresent()) {
                    var entity = entityOpt.get();
                    var removalConditionTable = entity.isEjectionPossible() ?
                        REMOVAL_CONDITIONS_TABLE : REMOVAL_CONDITIONS_TABLE_NO_EJECTION;
                    entity.setRemovalCondition(removalConditionTable.randomItem());

                    reporter.reportUnitDestroyed(entity);
                    getContext().addUnitToGraveyard(entity);
                }
            }

            formation.removeUnit(unit);
            if (formation.getUnits().isEmpty()) {
                getContext().removeFormation(formation);
            }
        }
    }
}
