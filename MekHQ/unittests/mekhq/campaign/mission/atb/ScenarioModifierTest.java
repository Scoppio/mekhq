/*
 * Copyright (C) 2020-2025 The MegaMek Team. All Rights Reserved.
 *
 * This file is part of MekHQ.
 *
 * MekHQ is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License (GPL),
 * version 3 or (at your option) any later version,
 * as published by the Free Software Foundation.
 *
 * MekHQ is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * A copy of the GPL should have been included with this project;
 * if not, see <https://www.gnu.org/licenses/>.
 *
 * NOTICE: The MegaMek organization is a non-profit group of volunteers
 * creating free software for the BattleTech community.
 *
 * MechWarrior, BattleMech, `Mech and AeroTech are registered trademarks
 * of The Topps Company, Inc. All Rights Reserved.
 *
 * Catalyst Game Labs and the Catalyst Game Labs logo are trademarks of
 * InMediaRes Productions, LLC.
 */
package mekhq.campaign.mission.atb;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author NickAragua
 */
public class ScenarioModifierTest {

    /**
     * Tests that the initial loading of the scenario modifier manifest works.
     */
    @Test
    public void testLoadScenarioModifierManifest() {
        assertNotNull(AtBScenarioModifier.getScenarioFileNames());
        assertNotEquals(0, AtBScenarioModifier.getScenarioFileNames().size());
    }

    /**
     * Tests that loading scenario modifiers from the manifest works.
     */
    @Test
    public void testLoadScenarioModifiersFromManifest() {
        assertNotNull(AtBScenarioModifier.getScenarioModifiers());
    }
}
