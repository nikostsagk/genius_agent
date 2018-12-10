import java.util.ArrayList;
import java.util.List;

import genius.core.AgentID;
import genius.core.Bid;
import genius.core.BidHistory;
import genius.core.Domain;
import genius.core.actions.Accept;
import genius.core.actions.Action;
import genius.core.actions.Offer;
import genius.core.bidding.BidDetails;
import genius.core.boaframework.OutcomeSpace;
import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.Value;
import genius.core.issue.ValueDiscrete;
import genius.core.misc.Range;
import genius.core.parties.AbstractNegotiationParty;
import genius.core.parties.NegotiationInfo;
import genius.core.uncertainty.AdditiveUtilitySpaceFactory;
import genius.core.utility.AdditiveUtilitySpace;
import genius.core.utility.EvaluatorDiscrete;

import java.util.HashMap;


/**
 * ExampleAgent returns the bid that maximizes its own utility for half of the negotiation session.
 * In the second half, it offers a random bid. It only accepts the bid on the table in this phase,
 * if the utility of the bid is higher than Example Agent's last bid.
 */
public class ExampleAgent extends AbstractNegotiationParty {
    private final String description = "Example Agent";

    private Bid lastReceivedOffer; // offer on the table
    private Bid myLastOffer;
    private HashMap<String, HashMap> issuesMap = new HashMap<>();
    private AdditiveUtilitySpace opponentsAdditiveUtilitySpace;
    private BidHistory bidHistoryOppHisUtility = new BidHistory();
    private BidHistory bidHistoryOppOurUtility = new BidHistory();
    private List<Bid> bidOrder;
    private static final int UPDATE_THREESHOLD = 10;
    private int offers_counter = 0;
    private AdditiveUtilitySpace additiveUtilitySpace2;
    private double psi = 0.3;  // psi < 1 -> boulware; psi > 1 -> conceader; psi == 1 -> linear;
    private boolean sendingSameOffer = false;
    private double delta;


        @Override
	public void init(NegotiationInfo info) {
	    super.init(info);
	    Domain domain = getDomain();
	    AdditiveUtilitySpaceFactory factory = new AdditiveUtilitySpaceFactory(domain);
	    factory.estimateUsingBidRanks(userModel.getBidRanking());
	    System.out.println("UTILITY_SPACE");
	    System.out.println(factory.getUtilitySpace());
	    System.out.println("UTILITY_SPACE");
	    AdditiveUtilitySpace additiveUtilitySpace = factory.getUtilitySpace();
	    additiveUtilitySpace2 = additiveUtilitySpace;
	    opponentsAdditiveUtilitySpace = (AdditiveUtilitySpace) additiveUtilitySpace.copy();
	    //        opponentsAdditiveUtilitySpace = additiveUtilitySpace;
	    bidOrder = userModel.getBidRanking().getBidOrder();
	    System.out.println(bidOrder);
	    System.out.println(bidOrder.size());
	    List<Issue> issues = additiveUtilitySpace.getDomain().getIssues();
	    int totalPossibleBids = 1;
	    for (Issue issue : issues) {
		int issueNumber = issue.getNumber();
		//System.out.println(">> " + issue.getName() + " weight: " + additiveUtilitySpace.getWeight(issueNumber));
		// Assuming that issues are discrete only
		IssueDiscrete issueDiscrete = (IssueDiscrete) issue;
		//EvaluatorDiscrete evaluatorDiscrete = (EvaluatorDiscrete) additiveUtilitySpace.getEvaluator(issueNumber);
		HashMap<String, Integer> valuesMap = new HashMap<String, Integer>();
		int noOfValues = 0;
		for (ValueDiscrete valueDiscrete : issueDiscrete.getValues()) {
		    // Initializing frequency list to 0 for each option
		    valuesMap.put(valueDiscrete.getValue(), 0);
		    //System.out.println(valueDiscrete.getValue());
		    //System.out.println("Evaluation(getValue): " + evaluatorDiscrete.getValue(valueDiscrete));
		    try {
			//System.out.println("Evaluation(getEvaluation): " + evaluatorDiscrete.getEvaluation(valueDiscrete));
		    } catch(Exception e) {
			//System.out.println("We have an exception");
		    }
		    noOfValues++;
		}
		totalPossibleBids *= noOfValues;
		issuesMap.put(issue.getName(), valuesMap);
	    }
	    System.out.println("All the possible bids:");
	    System.out.println(totalPossibleBids);
	    double delta = 0.000001*totalPossibleBids;
	    System.out.println("THE COMPLETE ISSUE MAP");
	    System.out.println(issuesMap);
	}

    public Bid generateRandomBidWithUtility(double utilityThreshold) {
	Bid randomBid;
	double utility;
	do {
	    randomBid = generateRandomBid();
	    try {
		utility = utilitySpace.getUtility(randomBid);
	    } catch (Exception e)
		{
		    utility = 0.0;
		}
	}
	while (utility < utilityThreshold);
	return randomBid;
    }

    private Bid generateBestBidWithinRangeForOpponent(double utilityThreshold) {
	//        BidDetails result = new BidDetails();
	OutcomeSpace outcomeSpace = new OutcomeSpace(utilitySpace);
	List<BidDetails> bids = outcomeSpace.getBidsinRange(new Range(utilityThreshold, 1.1));
	if(bids.size() < 1) {
	    try {
		return utilitySpace.getMaxUtilityBid();
	    } catch(Exception e) {
		return generateRandomBidWithUtility(utilityThreshold);
	    }
	}
	BidDetails result = bids.get(0);
	double resultUtility = 0;
	for (BidDetails bidDetails : bids) {
	    double bidUtility = opponentsAdditiveUtilitySpace.getUtility(bidDetails.getBid()) * bidDetails.getMyUndiscountedUtil();
	    //            System.out.println("Inside loop");
	    //            System.out.println(bidDetails.getBid());
	    //            System.out.println(bidDetails.getMyUndiscountedUtil());
	    //            System.out.println(bidUtility);
	    if(resultUtility < bidUtility) {
		result = bidDetails;
		resultUtility = bidUtility;
	    }
	}
	System.out.println("The selected bid");
	System.out.println(result.getBid());
	System.out.println(result.getMyUndiscountedUtil());
	System.out.println(resultUtility);
	return result.getBid();
    }

    /**
     * When this function is called, it is expected that the Party chooses one of the actions from the possible
     * action list and returns an instance of the chosen action.
     *
     * @param list
     * @return
     */
        @Override
	public Action chooseAction(List<Class<? extends Action>> list) {
	    // According to Stacked Alternating Offers Protocol list includes
	    // Accept, Offer and EndNegotiation actions only.
	    double time = getTimeLine().getTime(); // Gets the time, running from t = 0 (start) to t = 1 (deadline).
	    // The time is normalized, so agents need not be
	    // concerned with the actual internal clock.

	    // First half of the negotiation offering the max utility (the best agreement possible) for Example Agent
	    //        if (time < 0.5) {
	    //            Bid b = generateBestBidWithinRangeForOpponent((double) 1 - Math.pow(time, 1 / psi));
	    //            return new Offer(this.getPartyId(), b);
	    ////            return new Offer(this.getPartyId(), this.getMaxUtilityBid());
	    //        } else {
	    // Accepts the bid on the table in this phase,
	    // if the utility of the bid is higher than Example Agent's last bid.
	    if (lastReceivedOffer != null
		                && myLastOffer != null
		&& (this.utilitySpace.getUtility(lastReceivedOffer) * opponentsAdditiveUtilitySpace.getUtility(lastReceivedOffer) >= this.utilitySpace.getUtility(myLastOffer) * opponentsAdditiveUtilitySpace.getUtility(myLastOffer)-delta
		    || this.utilitySpace.getUtility(lastReceivedOffer) > opponentsAdditiveUtilitySpace.getUtility(lastReceivedOffer))){
		//                && this.getUtility(lastReceivedOffer) >= this.getUtility(myLastOffer)) {
		//                myLastOffer = generateBestBidWithinRangeForOpponent((double) 1 - Math.pow(time, 1 / psi));
		//            return new Offer(this.getPartyId(), myLastOffer);
		return new Accept(this.getPartyId(), lastReceivedOffer);
	    } else {
		// Offering a random bid
		double utility = (double) 1 - Math.pow(time, 1 / psi);
		myLastOffer = sendingSameOffer ?  generateRandomBidWithUtility(utility) : generateBestBidWithinRangeForOpponent(utility);
		return new Offer(this.getPartyId(), myLastOffer);
		//                return new Offer(this.getPartyId(), this.generateRandomBidWithUtility((double) 1 - Math.pow(time, 1 / psi)));
		//                myLastOffer = generateRandomBid();
		//                return new Offer(this.getPartyId(), myLastOffer);
	    }
	    //        }
	}

    private double johnnyBlackEstimateValue(int noOfOptions, int ranking) {
	noOfOptions = noOfOptions == 0 ? 1 : noOfOptions;
	double el1 = noOfOptions - ranking + 1;
	double el2 = (double) noOfOptions;
	return el1 / el2;
    }

    private double johnnyBlackEstimateIssueWeight(ArrayList<Integer> issueFrequencies, int totalFrequencies) {
	double result = 0;
	for(int frequency : issueFrequencies) {
	    result += Math.pow((double) frequency, 2) / Math.pow((double) totalFrequencies, 2);
	}
	return result;
    }

    private int getSortedIndex(HashMap valuesMap, List<ValueDiscrete> keys, int currentFrequency) {
	int result = 0;
	for(ValueDiscrete valueDiscrete : keys) {
	    String key = valueDiscrete.getValue();
	    int frequency = (int) valuesMap.get(key);
	    if(frequency > currentFrequency) {
		result += 1;
	    }
	}
	return result + 1;
    }

    /**
     * This method is called to inform the party that another NegotiationParty chose an Action.
     * @param sender
     * @param act
     */
        @Override
	public void receiveMessage(AgentID sender, Action act) {
	    super.receiveMessage(sender, act);

	    if (act instanceof Offer) { // sender is making an offer
		Offer offer = (Offer) act;
		lastReceivedOffer = offer.getBid();
		List<Issue> issues = lastReceivedOffer.getIssues();
		for (Issue issue : issues) {
		    EvaluatorDiscrete ed = new EvaluatorDiscrete();
		    String issueKey = issue.getName();
		    HashMap valuesMap = issuesMap.get(issueKey);
		    Value opponentsPreference = lastReceivedOffer.getValue(issue.getNumber());
		    valuesMap.put(opponentsPreference, new Integer((int)valuesMap.get(opponentsPreference) + 1));
		    valuesMap.get(lastReceivedOffer.getValue(issue.getNumber()));
		    HashMap<String, Double> opponentOptionsValues = new HashMap<>();
		    if(offers_counter >= UPDATE_THREESHOLD) {
			// Updating the opponents estimated utility values with johnny black technique
			ArrayList<Integer> frequencies = new ArrayList<>();
			IssueDiscrete issueDiscrete = (IssueDiscrete) issue;
			int numberOfOptions = issueDiscrete.getValues().size();
			int totalFrequencies = 0;
			for (ValueDiscrete valueDiscrete : issueDiscrete.getValues()) {
			    String valueKey = valueDiscrete.getValue();
			    int frequency = (int) valuesMap.get(valueKey);
			    double estimatedValue = johnnyBlackEstimateValue(numberOfOptions, getSortedIndex(valuesMap, issueDiscrete.getValues(), frequency));
			    opponentOptionsValues.put(valueKey, estimatedValue);
			    frequencies.add(frequency);
			    totalFrequencies += frequency;
			    ed.setEvaluationDouble(valueDiscrete, estimatedValue);
			}
			double estimatedIssueWeight = johnnyBlackEstimateIssueWeight(frequencies, totalFrequencies);
			ed.setWeight(estimatedIssueWeight);
			opponentsAdditiveUtilitySpace.addEvaluator(issue, ed);
		    }
		}
		bidHistoryOppHisUtility.add(new BidDetails(lastReceivedOffer, opponentsAdditiveUtilitySpace.getUtility(lastReceivedOffer)));
		bidHistoryOppOurUtility.add(new BidDetails(lastReceivedOffer, getUtility(lastReceivedOffer)));
		if(offers_counter >= UPDATE_THREESHOLD) {
		    System.out.println("OPPONENT ADDITIVE UTILITY SPACE BEFORE NORMALIZATION");
		    System.out.println(opponentsAdditiveUtilitySpace);
		    opponentsAdditiveUtilitySpace.normalizeWeights();
		    System.out.println("OPPONENT ADDITIVE UTILITY SPACE AFTER NORMALIZATION");
		    System.out.println(opponentsAdditiveUtilitySpace);
		    Bid bid = bidHistoryOppHisUtility.getHistory().get(bidHistoryOppHisUtility.size() - UPDATE_THREESHOLD - 1).getBid();
		    for (int i = bidHistoryOppHisUtility.size() - UPDATE_THREESHOLD; i < bidHistoryOppHisUtility.size(); i++) {
			if(bid.equals(bidHistoryOppHisUtility.getHistory().get(i).getBid())) {
			    sendingSameOffer = true;
			    System.out.println("THEY ARE THE SAME");
			} else {
			    sendingSameOffer = false;
			    System.out.println("THEY ARE NOT THE SAME");
			    break;
			}
		    }
		    offers_counter = 0;
		} else {
		    offers_counter += 1;
		}
		System.out.println("Printing last bid utility");
		System.out.println(opponentsAdditiveUtilitySpace);
		System.out.println("Printing the bid history");
		System.out.println(bidHistoryOppHisUtility.getLastBid());
		System.out.println(bidHistoryOppHisUtility.getLastBidDetails());
		System.out.println(additiveUtilitySpace2.getUtility(lastReceivedOffer));
		System.out.println(bidHistoryOppHisUtility.size());
	    } else {
		System.out.println("Bid hisotry opponent with his utility");
		System.out.println(bidHistoryOppHisUtility.getHistory());
		System.out.println("Bid history opponent our utility");
		System.out.println(bidHistoryOppOurUtility.getHistory());
	    }
	}

    /**
     * A human-readable description for this party.
     * @return
     */
        @Override
	public String getDescription() {
	    return description;
	}

    private Bid getMaxUtilityBid() {
	try {
	    return this.utilitySpace.getMaxUtilityBid();
	} catch (Exception e) {
	    e.printStackTrace();
	}
	return null;
    }
}
