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

package mekhq.campaign.autoresolve.acar.action;

import megamek.common.TargetRoll;
import mekhq.campaign.autoresolve.acar.SimulationContext;

public class EngagementControlToHitData extends TargetRoll {

    public EngagementControlToHitData(int value, String desc) {
        super(value, desc);
    }

    public static EngagementControlToHitData compileToHit(SimulationContext game, EngagementControlAction engagementControl) {
        if (engagementControl.isInvalid(game)) {
            return new EngagementControlToHitData(TargetRoll.IMPOSSIBLE, "Invalid engagement and control");
        }
        var attackingFormationOpt = game.getFormation(engagementControl.getEntityId());
        if (attackingFormationOpt.isEmpty()) {
            return new EngagementControlToHitData(TargetRoll.IMPOSSIBLE, "Invalid engagement and control");
        }

        var attackingFormation = attackingFormationOpt.get();
        var toHit = new EngagementControlToHitData(attackingFormation.getTactics(), "Tactics");
        processFormationModifiers(toHit, game, engagementControl);
        processMorale(toHit, game, engagementControl);
        processEngagementAndControlChosen(toHit, game, engagementControl);
        processSizeDifference(toHit, game, engagementControl);
        return toHit;
    }

    private static void processEngagementAndControlChosen(EngagementControlToHitData toHit, SimulationContext game, EngagementControlAction engagementControl) {
        switch (engagementControl.getEngagementControl()) {
            case FORCED_ENGAGEMENT:
                toHit.addModifier(-3, "Force engagement");
                break;
            case EVADE:
                toHit.addModifier(-3, "Evade");
                break;
            case OVERRUN:
                processSizeDifference(toHit, game, engagementControl);
                break;
            default:
                break;
        }
    }

    private static void processFormationModifiers(EngagementControlToHitData toHit, SimulationContext game, EngagementControlAction engagementControl) {
        var formationOpt = game.getFormation(engagementControl.getEntityId());
        if (formationOpt.isEmpty()) {
            return;
        }
        var formation = formationOpt.get();

        var formationIsInfantryOnly = formation.isInfantry();
        var formationIsVehicleOnly = formation.isVehicle();

        if (formationIsInfantryOnly) {
            toHit.addModifier(2, "Formation is infantry only");
        }
        if (formationIsVehicleOnly) {
            toHit.addModifier(1, "Formation is vehicle only");
        }
    }

    private static void processSizeDifference(EngagementControlToHitData toHit, SimulationContext game, EngagementControlAction engagementControl) {
        var attackerOpt = game.getFormation(engagementControl.getEntityId());
        var targetOpt = game.getFormation(engagementControl.getTargetFormationId());
        if (attackerOpt.isEmpty() || targetOpt.isEmpty()) {
            return;
        }
        int sizeDifference = attackerOpt.get().getSize() - targetOpt.get().getSize();
        toHit.addModifier(sizeDifference, "Formation size difference");
    }

    private static void processMorale(EngagementControlToHitData toHit, SimulationContext game, EngagementControlAction engagementControl) {
        var targetOpt = game.getFormation(engagementControl.getTargetFormationId());
        if (targetOpt.isEmpty()) {
            return;
        }
        switch (targetOpt.get().moraleStatus()) {
            case SHAKEN -> toHit.addModifier(+1, "Shaken morale");
            case UNSTEADY -> toHit.addModifier(+2, "Unsteady morale");
            case BROKEN -> toHit.addModifier(+3, "Broken morale");
            case ROUTED -> toHit.addModifier(TargetRoll.AUTOMATIC_FAIL, "Routed morale");
        }
    }
}