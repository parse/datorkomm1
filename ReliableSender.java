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
	private int oldestPosition;

	ReliableSender(Channel aChannel, int aWindowSize, double aTimeout) {
		channel = aChannel;
		timer = new Timer(this);
		windowSize = aWindowSize;
		timeoutValue = aTimeout;
		nextSeqNum = 1;
		oldestPosition = 1;
		
		window = new Packet[2][aWindowSize];
	}

	void send(Data aDataChunk) {
		
		if ( window[0][(nextSeqNum) % windowSize] != null) {
			blockData();
			oldestPosition = nextSeqNum % windowSize;
		}

		sentPacket = new Packet(nextSeqNum, aDataChunk);
		channel.send(sentPacket);	

		window[0][ (nextSeqNum-1) % windowSize ] = sentPacket;
		
		Simulator.getInstance().log("sender sent " + sentPacket);

		timer.start(timeoutValue);
		
		nextSeqNum++;
	}

	public void receive(Packet aPacket) {
		Simulator.getInstance().log("sender receives " + aPacket);
		int ackPosition = aPacket.getSeqNum() % windowSize;
		
		window[0][ackPosition] = null;
		
		if (ackPosition == oldestPosition) {
			acceptData();
			timer.stop();
		}
			
//		if (aPacket.getSeqNum() == nextSeqNum) {
//			nextSeqNum++;
//			acceptData();
//		}
	}

	public void timeout(Timer aTimer) {
		Simulator.getInstance().log("*** sender timeouts ***");
		
		for (int i = oldestPosition; i < (windowSize+oldestPosition); i++) {
			if (window[0][i % windowSize] != null) {
				Simulator.getInstance().log("sender resends " + window[0][i % windowSize]);
				channel.send(window[0][i % windowSize]);
			}
		}
		
		timer.start(timeoutValue);
		//channel.send(sentPacket);
	}
}
