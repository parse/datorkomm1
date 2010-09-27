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
			
			channel.send(rePacket);
			timer.start(timeoutValue);
			//Simulator.getInstance().log("*** Resends: " + rePacket + " (receive)");
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
