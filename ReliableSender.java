/**
 * @author Johan Nykvist, Daniel Aldman
 */
public class ReliableSender extends SenderProtocol {

	private Channel channel;

	private Timer timer;

	private int windowSize;

	private double timeoutValue;

	private Packet sentPacket;

	private Packet[][] window;
	
	private int nextSeqNum;
	private int oldestSeqNum;

	ReliableSender(Channel aChannel, int aWindowSize, double aTimeout) {
		channel = aChannel;
		timer = new Timer(this);
		windowSize = aWindowSize;
		timeoutValue = aTimeout;
		nextSeqNum = 1;
		oldestSeqNum = 1;
		
		window = new Packet[2][aWindowSize];
	}

	void send(Data aDataChunk) {
		
		if ( window[0][(nextSeqNum) % windowSize] != null) {
			blockData();
			oldestSeqNum = (nextSeqNum - windowSize)+1;
			Simulator.getInstance().log("Packet "+ oldestSeqNum + " (in send)");
		}

		sentPacket = new Packet(nextSeqNum, aDataChunk);
		channel.send(sentPacket);	

		window[0][ (nextSeqNum-1) % windowSize ] = sentPacket;
		
		Simulator.getInstance().log("sender sent " + sentPacket);

		timer.start(timeoutValue);
		
		nextSeqNum++;
	}

	public void receive(Packet aPacket) {
		int ackSeqNum = aPacket.getSeqNum();
		Simulator.getInstance().log("sender receives " + aPacket + "\nOldest: " + oldestSeqNum);

		if (ackSeqNum >= oldestSeqNum) {
			// We can move the window (oldestSeqNum)
			
			// All older packets should be set to null
			for (int i = oldestSeqNum; i <= ackSeqNum; i++) {
				window[0][i % windowSize] = null;
				Simulator.getInstance().log("Resetting " + i % windowSize);
			}
			
			oldestSeqNum = ackSeqNum + 1; 
			acceptData();
			timer.stop();
		}
		else { 
			// Window can't be moved - Resend requested
			Packet rePacket = window[0][(ackSeqNum + 1) % windowSize];
			
			timer.stop();
			
			if (rePacket != null) {
				Simulator.getInstance().log("sender resends " + rePacket);
				channel.send(rePacket);
				timer.start(timeoutValue);
			}
		}
	}

	public void timeout(Timer aTimer) {
		Simulator.getInstance().log("*** sender timeouts ***");
		int oldestPosition = (oldestSeqNum - 1) % windowSize;
		
		for (int i = oldestPosition; i < (windowSize+oldestPosition); i++) {
			if (window[0][i % windowSize] != null) {
				Simulator.getInstance().log("sender (timeout) resends " + window[0][i % windowSize]);
				channel.send(window[0][i % windowSize]);
			}
		}
		
		timer.start(timeoutValue);
		//channel.send(sentPacket);
	}
}
