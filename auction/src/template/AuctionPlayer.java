package template;

import java.util.ArrayList;
import java.util.HashSet;

import org.apache.commons.math.stat.descriptive.moment.StandardDeviation;

import logist.task.Task;
import logist.topology.Topology.City;
import uchicago.src.collection.Pair;

public class AuctionPlayer {

	private int id;
	private ArrayList<Task> wonTasks;
	private ArrayList<Long> pastBids;
	private double meanBid;
	private double bidStd;

	public AuctionPlayer(int id) {
		super();
		this.id = id;
		this.wonTasks = new ArrayList<>();
		this.pastBids = new ArrayList<>();
		this.meanBid = 0;
		this.bidStd = 0;
	}
	
	public void updatePlayerStatus(Task t, boolean wonAuction, Long bid) {
		if(wonAuction)
			this.wonTasks.add(t);
		int numPastBids = this.pastBids.size();
		this.pastBids.add(bid);
		this.meanBid = ((numPastBids * this.meanBid) + bid) / (double)(numPastBids + 1);
		this.bidStd = this.computeStd(this.pastBids.stream().mapToDouble(l -> l).toArray());
	}
	
	private double computeStd(double[] data) {
		StandardDeviation std = new StandardDeviation();
		return std.evaluate(data);
	}
	
	private int computeDissimilarityOfTask(Task t) {
		HashSet<City> citiesInPath = new HashSet<>();
		
		for(Task wonTask : this.wonTasks)
		{
			citiesInPath.add(wonTask.pickupCity);
			citiesInPath.add(wonTask.deliveryCity);
		}
		
		// TODO: Think about how to compute and use the depth/hop count of the task t's pickup and delivery cities
		int dissimilarity = 2;
		if(citiesInPath.contains(t.pickupCity))
			dissimilarity--;
		if(citiesInPath.contains(t.deliveryCity))
			dissimilarity--;
		
		return dissimilarity;
		
	}
	
	public Pair estimatePriceBin(Task t) {
		double lowerBoundInterval = this.meanBid - 2 * this.bidStd;
		double upperBoundInterval = this.meanBid + 2 * this.bidStd;
		
		double priceIntervalSize = (upperBoundInterval - lowerBoundInterval) / 3;
		int dissimilarity = this.computeDissimilarityOfTask(t);
		
		double lowerEstimate = lowerBoundInterval + dissimilarity * priceIntervalSize;
		double upperEstimate = lowerBoundInterval + (dissimilarity + 1) * priceIntervalSize;
		
		Pair pair = new Pair(lowerEstimate, upperEstimate);
		
		return pair;
	}

	public int getId() {
		return id;
	}

	public ArrayList<Task> getWonTasks() {
		return wonTasks;
	}
	
	public boolean hasWonTasks() {
		return !wonTasks.isEmpty();
	}

	public ArrayList<Long> getPastBids() {
		return pastBids;
	}

	public double getMeanBid() {
		return meanBid;
	}

	public double getBidStd() {
		return bidStd;
	}
	
	
}
