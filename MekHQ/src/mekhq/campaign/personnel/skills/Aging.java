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
package mekhq.campaign.personnel.skills;

import static mekhq.campaign.personnel.skills.enums.AgingMilestone.NONE;
import static mekhq.campaign.personnel.skills.enums.AgingMilestone.TWENTY_FIVE;
import static mekhq.campaign.personnel.skills.enums.SkillAttribute.NO_SKILL_ATTRIBUTE;

import java.time.LocalDate;

import mekhq.campaign.personnel.Person;
import mekhq.campaign.personnel.skills.enums.AgingMilestone;
import mekhq.campaign.personnel.skills.enums.SkillAttribute;

public class Aging {
    /**
     * A constant used to divide the sum of skill attribute modifiers in the aging calculations.
     *
     * <p>When calculating skill modifiers from aging, we take the ATOW values and then divide them by this value.
     * This is because modifying the experience a character has spent on {@code x} skill would get complicated quickly,
     * so we use this workaround.</p>
     */
    private static final int AGING_SKILL_MODIFIER_DIVIDER = 100;

    /**
     * Updates the aging modifiers for all skills of a given {@link Person} based on their current age and attributes.
     *
     * <p>This method calculates the appropriate aging modifier for each skill of a person using the person's age (as
     * of the provided date), associated {@link AgingMilestone}, and skill attributes. If {@code updateMissingOnly} is
     * true, only skills with an unset aging modifier are updated.</p>
     *
     * <p><strong>Behavior:</strong></p>
     * <ul>
     *     <li>Determines the {@link AgingMilestone} based on the person's age.</li>
     *     <li>Iterates over all skills defined in {@link SkillType#skillList}.</li>
     *     <li>If the person doesn't have a specific skill, it skips processing that skill.</li>
     *     <li>Retrieves the skill's attributes and calculates the aging modifier using {@link #getAgeModifier(AgingMilestone,
     *     SkillAttribute, SkillAttribute)}.</li>
     *     <li>Sets the calculated modifier for each applicable skill.</li>
     * </ul>
     *
     * @param today  the current date used to calculate the person's age
     * @param person the {@link Person} whose skills should be updated
     */
    public static void updateAllSkillAgeModifiers(LocalDate today, Person person) {
        AgingMilestone milestone = getMilestone(person.getAge(today));

        for (String skillName : SkillType.skillList) {
            boolean hasSkill = person.hasSkill(skillName);

            if (!hasSkill) {
                continue;
            }

            Skill skill = person.getSkill(skillName);

            SkillType type = SkillType.getType(skillName);
            SkillAttribute firstAttribute = type.getFirstAttribute();
            SkillAttribute secondAttribute = type.getSecondAttribute();

            int modifier = getAgeModifier(milestone, firstAttribute, secondAttribute);

            skill.setAgingModifier(modifier);
        }
    }

    public static void clearAllAgeModifiers(Person person) {
        for (String skillName : SkillType.skillList) {
            boolean hasSkill = person.hasSkill(skillName);

            if (!hasSkill) {
                continue;
            }

            Skill skill = person.getSkill(skillName);
            skill.setAgingModifier(0);
        }
    }

    /**
     * Calculates the age-related skill modifiers for a character based on their age and provided skill attributes.
     *
     * <p>This method determines the character's {@link AgingMilestone} based on their age and delegates the
     * computation of the age modifier to {@link #getAgeModifier(AgingMilestone, SkillAttribute, SkillAttribute)}.</p>
     *
     * <p><b>Usage:</b> This is an overload of the above method. You should try to use this method in the event that
     * you're only checking a single skill. As otherwise, it's better to calculate {@link AgingMilestone} once, and pass
     * that into all calls. Instead of needing to calculate it for each skill individually.</p>
     *
     * @param characterAge    the age of the character
     * @param firstAttribute  the first skill attribute to consider
     * @param secondAttribute the second skill attribute to consider
     *
     * @return the calculated skill attribute modifier after applying aging-related adjustments, or {@code 0} if no
     *       valid aging milestone applies
     */
    public static int getAgeModifier(int characterAge, SkillAttribute firstAttribute, SkillAttribute secondAttribute) {
        // Get the milestone for the character's age
        AgingMilestone milestone = getMilestone(characterAge);

        return getAgeModifier(milestone, firstAttribute, secondAttribute);
    }

    /**
     * Calculates the age-related skill attribute modifier for a character based on a given {@link AgingMilestone}.
     *
     * <p>This method retrieves the attribute modifiers for the provided skill attributes from the milestone, applies
     * a predefined adjustment formula, and returns the result. If both attributes have no valid skill modifiers, it
     * returns {@code 0}. If only one valid modifier exists, it applies the adjustment to that single modifier. If both
     * attributes have valid modifiers, their modifiers are summed and adjusted.</p>
     *
     * <p><b>Usage:</b> This is an alternative to the {@link #getAgeModifier(int, SkillAttribute, SkillAttribute)}
     * method and is best suited to instances where you're wanting to calculate the modifiers for multiple skills. In
     * those instances you calculate {@link AgingMilestone} once, and then pass it into this method for each skill. If
     * you're only needing to calculate a single skill, the above-cited method is better suited as it will lazily
     * calculate {@link AgingMilestone} for you.</p>
     *
     * <p><b>Behavior:</b></p>
     * <ul>
     *     <li>If the milestone is {@code NONE}, it returns {@code 0} immediately.</li>
     *     <li>If both skill attributes are invalid (e.g., {@code NO_SKILL_ATTRIBUTE}), the result is {@code 0}.</li>
     *     <li>If one attribute is valid, its modifier is adjusted and returned as the result.</li>
     *     <li>If both attributes are valid, the sum of their modifiers is adjusted and returned.</li>
     * </ul>
     *
     * @param milestone       the {@link AgingMilestone} applicable to the character's age
     * @param firstAttribute  the first skill attribute to consider
     * @param secondAttribute the second skill attribute to consider
     *
     * @return the calculated skill attribute modifier after applying aging-related adjustments, or {@code 0} if no 0}
     *       0} if no valid combination or milestone exists
     */
    public static int getAgeModifier(AgingMilestone milestone, SkillAttribute firstAttribute,
                                     SkillAttribute secondAttribute) {
        // If no milestone applies, return no modifier
        if (milestone == NONE) {
            return 0;
        }

        // Get the attribute modifiers for the provided attributes
        int firstModifier = milestone.getAttributeModifier(firstAttribute);
        int secondModifier = milestone.getAttributeModifier(secondAttribute);

        // Check for modifiers without skill attributes and compute accordingly
        if (firstModifier == NO_SKILL_ATTRIBUTE && secondModifier == NO_SKILL_ATTRIBUTE) {
            // No valid skill attributes, return no modifier (this likely suggests a malformed SkillValue)
            return 0;
        } else if (firstModifier == NO_SKILL_ATTRIBUTE) {
            return applyAgingModifier(secondModifier);
        } else if (secondModifier == NO_SKILL_ATTRIBUTE) {
            return applyAgingModifier(firstModifier);
        }

        // Average the two modifiers and apply the aging skill adjustment
        return applyAgingModifier((firstModifier + secondModifier) / 2);
    }

    /**
     * Determines the appropriate {@link AgingMilestone} for a given character's age.
     *
     * <p>If a character's age does not fall into the range of any milestone, it defaults to {@code NONE}.
     * This method is optimized to exit early for young characters whose age is below the milestone threshold.</p>
     *
     * @param characterAge the age of the character
     *
     * @return the matching {@link AgingMilestone} for the character's age, or {@code NONE} if no milestone is
     *       applicable
     */
    public static AgingMilestone getMilestone(int characterAge) {
        // Early exit, so we don't need to loop through all values for young characters
        if (characterAge < TWENTY_FIVE.getMilestone()) {
            return NONE;
        }

        for (AgingMilestone milestone : AgingMilestone.values()) {
            if ((characterAge >= milestone.getMilestone()) && (characterAge < milestone.getMaximumAge())) {
                return milestone;
            }
        }

        return NONE;
    }

    /**
     * Applies an aging adjustment to a given skill modifier sum.
     *
     * <p>The adjustment divides the sum by a predefined constant value, {@code AGING_SKILL_MODIFIER_DIVIDER},
     * and rounds to the nearest integer using {@code Math.round}.</p>
     *
     * @param modifierSum the sum of the skill attribute modifiers
     *
     * @return the adjusted skill attribute modifier after applying aging rules
     */
    private static int applyAgingModifier(int modifierSum) {
        return (int) Math.round((double) modifierSum / AGING_SKILL_MODIFIER_DIVIDER);
    }
}
