/**
 * Copyright (c) 2025-2025 The MegaMek Team. All Rights Reserved.
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

package mekhq.utilities;

import mekhq.campaign.enums.CampaignTransportType;
import mekhq.campaign.unit.Unit;

import java.util.*;

import static mekhq.campaign.enums.CampaignTransportType.SHIP_TRANSPORT;
import static mekhq.campaign.enums.CampaignTransportType.TACTICAL_TRANSPORT;

/**
 * We need to determine what units in game are transports, and what units they're transporting.
 * This map does most of the work and helps hold what transports are transporting what units
 * for a given campaign transport type.
 * @see CampaignTransportType
 */
public class PotentialTransportsMap {

    private final HashMap<CampaignTransportType, Map<UUID, List<UUID>>> hashMap = new HashMap<>();

    public PotentialTransportsMap(CampaignTransportType[] campaignTransportTypes) {
        for (CampaignTransportType campaignTransportType : campaignTransportTypes) {
            hashMap.put(campaignTransportType, new HashMap<>());
        }
    }

    /**
     * For the provided campaign transport type, are there any
     * transports in the map?
     * @see CampaignTransportType
     * @param campaignTransportType type (enum) of campaign transport
     * @return true if there are any transports in the map for the corresponding CampaignTransportType
     */
    public boolean hasTransports(CampaignTransportType campaignTransportType) {
        return hashMap.containsKey(campaignTransportType) && !(hashMap.get(campaignTransportType).isEmpty());
    }

    /**
     * For the provided campaign transport type, get the transports
     * @see CampaignTransportType
     * @param campaignTransportType type (enum) of campaign transport
     * @return transports for the given campaign transport type
     */
    public Set<UUID> getTransports(CampaignTransportType campaignTransportType) {
        return hashMap.get(campaignTransportType).keySet();
    }

    /**
     * For the provided campaign transport type and transport id, get the transported units
     * @param campaignTransportType type (enum) of campaign transport
     * @param uuid transport id
     * @return list of uuids of units on that transport
     * @see CampaignTransportType
     */
    public List<UUID> getTransportedUnits(CampaignTransportType campaignTransportType, UUID uuid) {
        return hashMap.get(campaignTransportType).get(uuid);
    }

    /**
     * For the provided campaign transport type, does the provided transport exist in the map?
     * @param campaignTransportType type (enum) of campaign transport
     * @param key transport id
     * @return true if that transport exists, false if not
     */
    public boolean containsTransportKey(CampaignTransportType campaignTransportType, UUID key) {
       return hashMap.containsKey(campaignTransportType) && hashMap.get(campaignTransportType).containsKey(key);
   }

    /**
     * For the provided campaign transport type, add a transport
     * @param campaignTransportType type (enum) of campaign transport
     * @param key transport id
     */
    public void putNewTransport(CampaignTransportType campaignTransportType, UUID key) {
        hashMap.get(campaignTransportType).put(key, new ArrayList<>());
    }

    /**
     * Look through the transport map for this unit's assigned transports
     * in priority order (Ship then Tactical Transports). If the transport
     * is in the map, add it to the Map for loading later.
     * @param unit the Unit we want to transport on its assigned transport, if it has one
     */
    public void tryToAddTransportedUnit(Unit unit) {
        if (unit.hasTransportShipAssignment()) {
            Unit transportShip = unit.getTransportShipAssignment().getTransportShip();

            if (containsTransportKey(SHIP_TRANSPORT, transportShip.getId())) {
                addTransportedUnit(SHIP_TRANSPORT, transportShip.getId(), unit.getId());
                return;
            }
        }
        if ( unit.hasTacticalTransportAssignment()) {
            Unit transport = unit.getTacticalTransportAssignment().getTransport();

            if (containsTransportKey(TACTICAL_TRANSPORT, transport.getId())) {
                addTransportedUnit(TACTICAL_TRANSPORT, transport.getId(), unit.getId());
                return;
            }
        }
    }

    /**
     * For the provided campaign transport type and transport, add a transported unit
     * @param campaignTransportType type (enum) of campaign transport
     * @param key transport unit id
     * @param value transported unit id
     */
    public void addTransportedUnit(CampaignTransportType campaignTransportType, UUID key, UUID value) {
        hashMap.get(campaignTransportType).get(key).add(value);
    }

    /**
     * Removes any transports that are empty from the map so they don't need referenced anymore
     */
    public void removeEmptyTransports() {
        if (hashMap.isEmpty()) {
            return;
        }
        for (CampaignTransportType campaignTransportType : hashMap.keySet()) {
            Set<UUID> emptyTransports = new HashSet<>();
            if (!(hashMap.get(campaignTransportType).isEmpty())) {
                for (UUID transport : hashMap.get(campaignTransportType).keySet()) {
                    if (hashMap.get(campaignTransportType).get(transport).isEmpty()) {
                        emptyTransports.add(transport);
                    }
                }
            }
            for (UUID emptyTransport : emptyTransports) {
                hashMap.get(campaignTransportType).remove(emptyTransport);
            }
        }
    }
}