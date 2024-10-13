import io.nuls.contract.sdk.*;
import io.nuls.contract.sdk.annotation.Payable;
import io.nuls.contract.sdk.annotation.Required;
import io.nuls.contract.sdk.annotation.View;
import org.checkerframework.checker.units.qual.A;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nuls.contract.sdk.Utils.emit;
import static io.nuls.contract.sdk.Utils.require;

/**
 * @title   Nuls Oracles Revenue Distribution Contract
 *
 * @dev     Allows for users to deposit Nuls Oracle Tokens (ORA)
 *          and earn revenue from the Nuls Oracles Project.
 *          After deposited users will no longer be able to
 *          withdraw the tokens, meanign that by depositing
 *          they are burning their tokens.
 *
 * @author  Pedro G. S. Ferreira
 *
 */
public class Profits implements Contract{

    /// Constants
    private static BigInteger MIN_NULS_AMOUNT = BigInteger.valueOf(1_000_000);    // Minimum Nuls transferable amount

    /// Variables
    private Address     rewardDistribution;                    // Address that manages Contract admin functions
    private boolean     locked          = false;               // Prevent Reentrancy Attacks
    private BigInteger  allTimeRewards  = BigInteger.ZERO;     // All time Distributed Profits

    private Map<Address, BigInteger>    allTimeRewardsPerUser  = new HashMap<Address, BigInteger>();    // All Time Profits Earned by Shareholders
    private Map<Address, Boolean>       shareholder            = new HashMap<Address, Boolean>();       // Approved Shareholders

    private List<Address> shareholdersList = new ArrayList<Address>(); // Shareholder Lists

    /**
     * Constructor
     *
     * */
    public Profits() {
        rewardDistribution  = Msg.sender();
    }

    /*
    *
    *
    *
    */
    public void initialize(String[] shareholders_){
        for(int i = 0; i < shareholders_.length; i++) {
            shareholder.put(new Address(shareholders_[i]), true);
            shareholdersList.add(new Address(shareholders_[i]));
        }
    }

    /*===========================================

      VIEWS

     ===========================================*/

    /**
     * Returns Rewards Distrbution/Admin Address
     *
     * @return Rewards distribution/Admin address
     */
    @View
    public Address getRewardDistribution(){
        return rewardDistribution;
    }

    /**
     * Returns Lock Status
     *
     * @return lock status
     */
    @View
    public boolean getLockStatus(){
        return locked;
    }



    /**
     *  Returns all time rewards in Nuls
     *
     * @return all time rewards in Nuls
     */
    @View
    public BigInteger allTimeEarned(Address account) {
        return allTimeRewards;
    }


    /**
     * Returns Nuls Oracle Tokens (ORA) balance
     *
     * @param account User address
     * @return User Nuls Oracle Tokens (ORA) balance
     */
    @View
    public BigInteger _balanceOf() {
        return Msg.address().balance();
    }

    @View
    public BigInteger getShareholdersProfits(Address account) {
        if(allTimeRewardsPerUser.get(account) == null){
            return BigInteger.ZERO;
        }
        return allTimeRewardsPerUser.get(account);
    }

    /*===========================================

      Modifiers

     ===========================================*/

    /**
     * @dev Locks Contract in order to prevent reentrancy attacks
     * */
    protected void nonReentrant(){
        require(!locked, "Already Entered");
        locked = true;
    }

    /**
     * @dev Unlocks Contract after vulnerable operations are made
     * */
    protected void closeReentrant(){
        require(locked, "Not Entered");
        locked = false;
    }

    /**
     * @dev Only allow Rewards Distribution Address to call function
     * */
    private void onlyRewardDistribution() {
        require(Msg.sender().equals(rewardDistribution), "Caller is not reward distribution");
    }

    /*===========================================

      NON-ADMIN STATE MODIFIABLE FUNCTIONS

     ===========================================*/

    @Override
    @Payable
    public void _payable() {}

    /**
     *  Deposits Nuls Oracle Tokens (ORA) in the Contract
     *  and updated user balances
     *
     * @param amount Amount of ORA Tokens to deposit
     */
    @Payable
    public void profitDistribution(BigInteger amount) {

        // Prevent Reentrancy Attacks
        nonReentrant();

        BigInteger profits = Msg.address().balance();

        BigInteger individualProfits = profits.divide(BigInteger.valueOf(shareholdersList.size()));

        if(individualProfits.compareTo(MIN_NULS_AMOUNT) >= 0) {

            for (int i = 0; i < shareholdersList.size(); i++) {

                shareholdersList.get(i).transfer(individualProfits);

                if(allTimeRewardsPerUser.get(i) == null){
                    allTimeRewardsPerUser.put(shareholdersList.get(i), individualProfits);
                }else{
                    allTimeRewardsPerUser.put(shareholdersList.get(i), allTimeRewardsPerUser.get(i).add(individualProfits));
                }

                // Emit event with the Stake event
                emit(new RewardPaid(shareholdersList.get(i), individualProfits));
            }

            allTimeRewards = allTimeRewards.add(profits);
        }

        // Close Reentrancy Attacks Prevention
        closeReentrant();
    }

    public void removeShareholder(Address admin_) {

        require(shareholder.get(admin_) != null, "Not Shareholder");
        require(shareholder.get(admin_), "Not Shareholder");

        shareholder.put(admin_, false);
        shareholdersList.remove(admin_);
    }

    public void addShareholder(Address admin_) {

        require(shareholder.get(admin_) != null, "Invalid Shareholder");
        require(!shareholder.get(admin_), "Already Shareholder");

        shareholder.put(admin_, true);
        shareholdersList.add(admin_);
    }


    /*===========================================

      ADMIN STATE MODIFIABLE FUNCTIONS

     ===========================================*/

    /**
     *  Set New Rewards Distribution/Admin Address
     *
     * @param _rewardDistribution new rewards distrbution/admin address
     */
    public void setRewardDistribution(Address _rewardDistribution) {
        onlyRewardDistribution();
        rewardDistribution = _rewardDistribution;
    }


    /**
     * Recover Nuls funds lost in contract
     */
    public void recoverNuls() {
        //Only rewarder address can give reward
        onlyRewardDistribution();

        Msg.sender().transfer(Msg.address().balance());
    }

    /*====================================
    *
    * Events
    *
    * ====================================*/


    class RewardPaid implements Event {
        private Address user;
        private BigInteger amount;

        public RewardPaid(Address user, BigInteger amount) {
            this.user = user;
            this.amount = amount;
        }

        public Address getUser() {
            return user;
        }

        public void setUser(Address user) {
            this.user = user;
        }

        public BigInteger getAmount() {
            return amount;
        }

        public void setAmount(BigInteger amount) {
            this.amount = amount;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            RewardPaid that = (RewardPaid) o;

            if (user != null ? !user.equals(that.user) : that.user != null) return false;
            return amount != null ? amount.equals(that.amount) : that.amount == null;
        }

        @Override
        public int hashCode() {
            int result = user != null ? user.hashCode() : 0;
            result = 31 * result + (amount != null ? amount.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "Withdrawn{" +
                    "user=" + user +
                    ", amount=" + amount +
                    '}';
        }
    }

}