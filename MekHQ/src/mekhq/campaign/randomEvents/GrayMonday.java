/*
 * Copyright (C) 2025 The MegaMek Team. All Rights Reserved.
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
package mekhq.campaign.randomEvents;

import static java.time.temporal.ChronoUnit.DAYS;
import static mekhq.campaign.Campaign.AdministratorSpecialization.LOGISTICS;
import static mekhq.campaign.finances.enums.TransactionType.STARTING_CAPITAL;
import static mekhq.utilities.MHQInternationalization.getFormattedTextAt;

import java.time.LocalDate;

import megamek.common.annotations.Nullable;
import mekhq.campaign.Campaign;
import mekhq.campaign.finances.Finances;
import mekhq.campaign.finances.Money;
import mekhq.campaign.mission.AtBContract;
import mekhq.campaign.personnel.Person;
import mekhq.gui.dialog.randomEvents.GrayMondayDialog;

public class GrayMonday {
    private static final String RESOURCE_BUNDLE = "mekhq.resources.GrayMonday";

    public final static LocalDate EVENT_DATE_CLARION_NOTE = LocalDate.of(3132, 8,4);
    public final static LocalDate EVENT_DATE_GRAY_MONDAY = LocalDate.of(3132, 8,7);

    private final Campaign campaign;

    public GrayMonday(Campaign campaign, LocalDate today) {
        this.campaign = campaign;
        Person speaker = getSpeaker();

        int daysAfterClarionNote = (int) DAYS.between(EVENT_DATE_CLARION_NOTE, today);
        if (daysAfterClarionNote >= 0 && daysAfterClarionNote <= 3) {
            new GrayMondayDialog(campaign, speaker, true, daysAfterClarionNote);
        }

        int daysAfterGrayMonday = (int) DAYS.between(EVENT_DATE_GRAY_MONDAY, today);
        if (daysAfterGrayMonday > 0 && daysAfterGrayMonday <= 4) {
            boolean shouldShowDialog = daysAfterGrayMonday != 3;

            if (daysAfterGrayMonday == 3) {
                for (AtBContract contract : campaign.getAtBContracts()) {
                    LocalDate startDate = contract.getStartDate();
                    if (!startDate.isBefore(today)) {
                        shouldShowDialog = true;
                        break;
                    }
                }
            }

            if (shouldShowDialog) {
                new GrayMondayDialog(campaign, speaker, false, daysAfterGrayMonday);
            }
        }

        if (daysAfterGrayMonday == 2) {
            Finances finances = campaign.getFinances();
            Money balance = finances.getBalance();
            Money adjustedBalance = balance.multipliedBy(0.01);

            finances.getTransactions().clear();

            finances.getLoans().clear();

            finances.credit(STARTING_CAPITAL, today, adjustedBalance,
                getFormattedTextAt(RESOURCE_BUNDLE, "transaction.message"));

        }

        if (daysAfterGrayMonday == 3) {
            for (AtBContract contract : campaign.getAtBContracts()) {
                LocalDate startDate = contract.getStartDate();
                if (!startDate.isBefore(today)) {
                    contract.setBaseAmount(Money.of(0));
                    contract.setOverheadComp(0);
                    contract.setBattleLossComp(0);
                    contract.setStraightSupport(0);
                    contract.setTransportComp(0);
                    contract.setTransitAmount(Money.of(0));
                    contract.calculateContract(campaign);

                    contract.setSalvagePct(100);
                }
            }

            campaign.getContractMarket().getContracts().clear();

            getFormattedTextAt(RESOURCE_BUNDLE, "employer.report");
        }
    }

    /**
     * Retrieves the speaker for the dialogs.
     *
     * <p>The speaker is determined as the senior administrator personnel with the Logistics
     * specialization within the campaign. If no such person exists, this method returns {@code null}.</p>
     *
     * @return a {@link Person} representing the left speaker, or {@code null} if no suitable speaker is available
     */
    private @Nullable Person getSpeaker() {
        return campaign.getSeniorAdminPerson(LOGISTICS);
    }

    /**
     * Determines whether the current date falls within the Gray Monday event period.
     *
     * <p>This method checks if the Gray Monday event is enabled and whether the given date
     * falls within the defined period of the Gray Monday event.
     *
     * @param today           The current date as a {@link LocalDate} object.
     * @param isUseGrayMonday A {@code boolean} flag indicating whether the campaign is tracking
     *                       Gray Monday.
     * @return {@code true} if Gray Monday is active for the given date and campaign configuration,
     * {@code false} otherwise.
     */
    public static boolean isGrayMonday(LocalDate today, boolean isUseGrayMonday) {
        return isUseGrayMonday
            && today.isAfter(EVENT_DATE_GRAY_MONDAY.minusDays(1)) &&
                     today.isBefore(EVENT_DATE_GRAY_MONDAY.plusMonths(12));
    }
}
