/**
 * @author Anders Hassis, Daniel Lervik
 */
public class ReliableSender extends SenderProtocol {

	private Channel channel;

	private Timer timer;

	private int windowSize;

	private double timeoutValue;

	private Packet sentPacket;

	private Packet[] window;
	
	private int nextSeqNum;
	private int oldestSeqNum;

	ReliableSender(Channel aChannel, int aWindowSize, double aTimeout) {
		channel = aChannel;
		timer = new Timer(this);
		windowSize = aWindowSize;
		timeoutValue = aTimeout;
		nextSeqNum = 1;
		oldestSeqNum = 1;
		
		window = new Packet[aWindowSize];
	}

	void send(Data aDataChunk) {
		// nextSeqNum is equal to the seq num that is going to be sent.
		// Check the nextSeqNum + 1 and -1 since the spot 0 is for e.g. seq num 1
		// If it is not null --> We can not take more packets in the window --> Block state
		if (windowSize == 1 || window[nextSeqNum % windowSize] != null)
			blockData();

		// Create the packet with the nextSeqNum with the data and put it onto the channel
		sentPacket = new Packet(nextSeqNum, aDataChunk);
		channel.send(sentPacket);	

		// Put in the packet into the window, so that we can reuse this packet when needed
		window[(nextSeqNum-1) % windowSize] = sentPacket;

		// Start the timeout timer
		timer.start(timeoutValue);
		
		// Add up the nextSeqNum so that we have the right number next time we arrive in send
		nextSeqNum++;		
		
		//Simulator.getInstance().log("* Sent: " + sentPacket + " (send)");
	}

	public void receive(Packet aPacket) {
		int ackSeqNum = aPacket.getSeqNum();
		//Simulator.getInstance().log("** Receives: " + aPacket + " (receive) - Oldest: " + oldestSeqNum);

		// If the ackSeqNum is higher or equal to the oldestSeqNum it means we can move the window
		// and that the receiver has got good stuff
		if (ackSeqNum >= oldestSeqNum) {
			// All older packets should be set to null so that we get empty spaces in the array
			for (int i = oldestSeqNum; i <= ackSeqNum; i++)
				window[(i - 1) % windowSize] = null;

			// We can move the window (oldestSeqNum)
			oldestSeqNum = ackSeqNum + 1; 
			acceptData();
			
			// If position of the oldestSeqNum is empty, the window is empty
			if (window[(oldestSeqNum - 1) % windowSize] == null)
				timer.stop();
		} else { 
			// Window can't be moved - Resend requested
			Packet rePacket = window[ackSeqNum % windowSize];
			
			timer.stop();
			
			if (rePacket != null) {
				channel.send(rePacket);
				timer.start(timeoutValue);
				//Simulator.getInstance().log("*** Resends: " + rePacket + " (receive)");
			}
		}
	}

	public void timeout(Timer aTimer) {
		timer.stop();

		Simulator.getInstance().log("******* Timeout *******");
		int oldestPosition = (oldestSeqNum - 1) % windowSize;
		
		// Resend not null entries in window
		for (int i = oldestPosition; i < (windowSize+oldestPosition); i++) {
			if (window[i % windowSize] != null) {
				//Simulator.getInstance().log("**** Resends " + window[i % windowSize] + " (timeout)");
				channel.send(window[i % windowSize]);
			}
		}
		
		// Reset the timer
		timer.start(timeoutValue);
	}
}




/**
	Answers to all funny questions
	
	Task 2:
	The given stop-and-wait protocol sends one data packet at a time. Calculate the total transfer time of 100 data packets.
		Consider that it takes 0,001s before all bits are on the physical layer.
		Then it takes 0,01s for each bit to travel to the receiver from the sender or vice versa.
		
		The sender has transmission delay of 0,001s to send a packet, 
		and we assume the ack packet takes 0,001s to put on the channel.
		
		Each way takes 0,01s to travel.
		
		==> 100 * 2 * (0,01 + 0,001) = 2,2s
	
	Run a simulation and compare the result with your calculation. Do you get the same total transfer time? If not, why?
		It is correct assuming no packet loss
		
	
	
	Task 4
	Calculate the "optimal" minimum window size that gives the shortest possible total transfer time.
		We want to continiously push bits on the physical layer 
		==> 1 + Number of packets that can fit on one propagation delay
		
		We also get ack packets
		==> double up the window size
		
		2 * ((propagationDelay / transferDelay) + 1)
		
		In the specific case:
		2 * ((0,01 / 0,001) + 1) = 22

	Run a simulation with the optimal window size that you have calculated.
	Also, run simulations with smaller and larger window sizes and compare the total transfer times.
	Remember to set the number of data chunks to more than twice the maximum window size you test. 
	Why is this important? 
		We need to be sure to get a steady state. By doubling the packets we are sure that we are steady.
	What are your conclusions?
		We should not have too large window, since there will be overhead then.
	
	Calculate the "optimal" timeout value, if the channel were to drop packets.
		Assuming a static route we know when we should receive the packet ack. 
		If we haven't got it by then, we just timeout.
		
		Calculation (assuming the timer is started after the packet is on the physical layer):
		2*(transmission delay + propagation delay)
		
	What would be the effect of using a longer timeout value? 
		Say that the ack pack for one packet got lost, but all data has been received.
		Then we don't have a need for a timeout since we will get a ack pack for the next data sent.
		We then know that all data has been received and we don't need to timeout.
		
		The downside is longer response times.
			
	What would be the effect of using a shorter timeout value?
		Then we will always timeout for every packet sent.
 */