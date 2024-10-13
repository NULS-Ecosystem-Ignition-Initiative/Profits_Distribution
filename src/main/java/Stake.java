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
public class Stake implements Contract{

    /// Constants
    private static BigInteger MIN_NULS_AMOUNT = BigInteger.valueOf(1_000_000);    // Minimum Nuls transferable amount

    /// Variables
    private long    lastUpdateTime;                     // Last time when rewards were updated
    private Address treasury;                           // Treasury Address that will receive Contract Revenue
    private Address rewardDistribution;                 // Address that manages Contract admin functions

    private boolean     locked                = false;              // Prevent Reentrancy Attacks
    private BigInteger  _totalSupply          = BigInteger.ZERO;    // Total upply deposited in Contract

    private Map<Address, BigInteger> allTimeRewards         = new HashMap<Address, BigInteger>(); // All Time Rewards Earned by User
    private Map<Address, Boolean> shareholder            = new HashMap<Address, Boolean>(); // User ORA Tokens Deposited
    private List<Address> shareholdersList = new ArrayList<Address>;

    /**
     * Constructor
     *
     * @param depositToken Staking token
     * @param treasury  Treasury Address that will receive Contract Revenue
     * */
    public Stake(Address treasury) {

        this.treasury           = treasury;
        rewardDistribution      = Msg.sender();
    }

    public initialize(String[] shareholders){
        for(int i = 0; i < shareholders_.length; i++) {
            shareholdersList.add(new Address(shareholders[i]));
        }
    }

    /*===========================================

      VIEWS

     ===========================================*/

    /**
     * Returns Treasury Address
     *
     * @return treasury address
     */
    @View
    public Address getTreasuryAddress(){
        return treasury;
    }

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
     * Returns all the Nuls Oracle Tokens (ORA) deposited
     *
     * @return All Nuls Oracle Tokens deposited
     */
    @View
    public BigInteger totalSupply() {
        return _totalSupply;
    }


    /**
     *  Returns all time rewards in Nuls
     *
     * @return all time rewards in Nuls
     */
    @View
    public BigInteger allTimeEarned(Address account) {
        if(allTimeRewards.get(account) != null)
            return allTimeRewards.get(account).add(_earned(account));
        return _earned(account);
    }

    /**
     * Returns user reward per token deposited
     *
     * @param account User address
     * @return user reward per token deposited
     */
    @View
    public BigInteger getUserRewardPerTokenPaid(Address account) {
        if (userRewardPerTokenPaid.get(account) != null) {
            return userRewardPerTokenPaid.get(account);
        } else {
            return BigInteger.ZERO;
        }
    }

    /**
     * Returns user rewards already allocated to user
     *
     * @param account User address
     * @return user rewards already allocated to user
     */
    @View
    public BigInteger getUserAlreadyStoredRewards(Address account) {
        if (rewards.get(account) != null) {
            return rewards.get(account);
        } else {
            return BigInteger.ZERO;
        }
    }

    /**
     * Returns Nuls Oracle Tokens (ORA) balance
     *
     * @param account User address
     * @return User Nuls Oracle Tokens (ORA) balance
     */
    @View
    public BigInteger _balanceOf(Address account) {
        if (_balances.get(account) != null) {
            return _balances.get(account);
        } else {
            return BigInteger.ZERO;
        }
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

        BigInteger individualProfits = profits.divide(shareholdersList.size());

        if(individualProfits.compareTo(MIN_NULS_AMOUNT) >= 0) {
            for (int i = 0; i < shareholdersList.size(); i++) {
                shareholdersList.get(i).transfer(individualProfits);
            }
        }
        // Emit event with the Stake event
        emit(new Staked(Msg.sender(), amount));

        // Close Reentrancy Attacks Prevention
        closeReentrant();
    }


    /*===========================================

      ADMIN STATE MODIFIABLE FUNCTIONS

     ===========================================*/


    /**
     * Set New Treasury Address
     *
     * @param addr new Treasury Address
     */
    public void setTreasury(Address addr){
        onlyRewardDistribution();
        require(addr != null, "Invalid Treasury Address");
        treasury = addr;
    }


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
     * Recover Token funds lost in contract
     *
     * @param tkn_ Token Address of assets to be recovered
     */
    public void recoverNRC20(Address tkn_) {
        //Only rewarder address can give reward
        onlyRewardDistribution();

        require(tkn_ != null, "Token Must be non-zero");

        String[][] argI = new String[][]{new String[]{Msg.address().toString()}};
        BigInteger b = new BigInteger(tkn_.callWithReturnValue("balanceOf", "", argI, BigInteger.ZERO));

        safeTransfer(tkn_, rewardDistribution, b);
    }

    /**
     * Recover Nuls funds lost in contract
     */
    public void recoverNuls() {
        //Only rewarder address can give reward
        onlyRewardDistribution();

        Msg.sender().transfer(Msg.address().balance());
    }



    /*===========================================

      PRIVATE FUNCTIONS

     ===========================================*/

    /**
     * Updates rewards earned by user before any new operations are done,
     *  if user is null updates rewards per token stored only
     *
     * @param account User Address
     */
    private void updateReward(Address account) {
        rewardPerTokenStored = rewardPerToken();
        lastUpdateTime = lastTimeRewardApplicable();
        if (account != null) {
            rewards.put(account, _earned(account));
            userRewardPerTokenPaid.put(account, rewardPerTokenStored);
        }
    }

    /**
     * Returns all rewards per token deposited
     *
     * @return Rewards per token deposited
     */
    private BigInteger rewardPerToken() {
        if (_totalSupply.equals(BigInteger.ZERO)) {
            return rewardPerTokenStored;
        }

        return rewardPerTokenStored.
                add(BigInteger.valueOf(lastTimeRewardApplicable()).
                        subtract(BigInteger.valueOf(lastUpdateTime)).
                        multiply(rewardRate).
                        multiply(BigInteger.valueOf((long) 1e8)).
                        divide(_totalSupply));
    }

    /**
     *  Get all the rewards earned by a user, all stored and all not stored
     *
     * @param account User Address
     *
     * @return user earned rewards
     */
    private BigInteger _earned(Address account) {
        BigInteger userRewardPer = BigInteger.ZERO;
        if (userRewardPerTokenPaid.get(account) != null) {
            userRewardPer = userRewardPer.add(userRewardPerTokenPaid.get(account));
        }
        BigInteger reward = BigInteger.ZERO;
        if (rewards.get(account) != null) {
            reward = reward.add(rewards.get(account));
        }
        return _balanceOf(account).multiply(rewardPerToken().subtract(userRewardPer)).
                divide(BigInteger.valueOf((long) 1e8)).add(reward);

    }

    /**
     *  Transfer token from this contract to recipient address
     *
     * @param token Token Address
     * @param recipient Recipient Address
     * @param amount Amount to Transfer
     */
    private void safeTransfer(@Required Address token, @Required Address recipient, @Required BigInteger amount){
        String[][] argsM = new String[][]{new String[]{recipient.toString()}, new String[]{amount.toString()}};
        boolean b = new Boolean(token.callWithReturnValue("transfer", "", argsM, BigInteger.ZERO));
        require(b, "NulswapV1: Failed to transfer");
    }

    /**
     *  Transfer token from the address from to the recipient address
     *
     * @param token Token Address
     * @param from The Address from where the tokens will be retrieved
     * @param recipient Recipient Address
     * @param amount Amount to Transfer
     */
    private void safeTransferFrom(@Required Address token, @Required Address from, @Required Address recipient, @Required BigInteger amount){
        String[][] args = new String[][]{new String[]{from.toString()}, new String[]{recipient.toString()}, new String[]{amount.toString()}};
        boolean b = new Boolean(token.callWithReturnValue("transferFrom", "", args, BigInteger.ZERO));
        require(b, "NulswapV1: Failed to transfer");
    }

    /**
     *  Get address allowance to spend funds from other address
     *
     * @param token Token Address
     * @param owner Holder of funds Address
     * @param mover Address who wants to transfer funds from owner
     */
    private BigInteger getUserAllowance(@Required Address token, @Required Address owner, @Required Address mover){
        String[][] args = new String[][]{new String[]{owner.toString()}, new String[]{mover.toString()}};
        BigInteger b = new BigInteger(token.callWithReturnValue("allowance", "", args, BigInteger.ZERO));
        return b;
    }

    /*====================================
    *
    * Events
    *
    * ====================================*/


    class Staked implements Event {
        private Address user;
        private BigInteger amount;

        public Staked(Address user, BigInteger amount) {
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

            Staked that = (Staked) o;

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
            return "Staked{" +
                    "user=" + user +
                    ", amount=" + amount +
                    '}';
        }
    }

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


    class RewardAdded implements Event {
        private BigInteger reward;

        public RewardAdded(BigInteger reward) {
            this.reward = reward;
        }

        public BigInteger getReward() {
            return reward;
        }

        public void setReward(BigInteger reward) {
            this.reward = reward;
        }

        @Override
        public String toString() {
            return "RewardAdded{" +
                    "reward=" + reward +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            RewardAdded that = (RewardAdded) o;

            return reward != null ? reward.equals(that.reward) : that.reward == null;
        }

        @Override
        public int hashCode() {
            int result = reward != null ? reward.hashCode() : 0;
            return result;
        }
    }



}