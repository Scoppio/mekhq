package mekhq.campaign.market.contractMarket;

import mekhq.campaign.Campaign;
import mekhq.campaign.market.enums.ContractMarketMethod;
import mekhq.campaign.mission.AtBContract;

public class DisabledContractMarket extends AbstractContractMarket {
    public DisabledContractMarket() {
        super(ContractMarketMethod.NONE);
    }

    @Override
    public AtBContract addAtBContract(Campaign campaign) {
        return null;
    }

    @Override
    public void generateContractOffers(Campaign campaign, boolean newCampaign) {

    }

    @Override
    public void checkForFollowup(Campaign campaign, AtBContract contract) {

    }

    @Override
    public double calculatePaymentMultiplier(Campaign campaign, AtBContract contract) {
        return 1.0;
    }
}
