package edu.duke.raft;

import java.util.Arrays;
import java.util.Timer;

public class LeaderMode extends RaftMode {
	private Timer myCurrentTimer;
	private int[] nextIndex;

	// TODO: Ask TA Do we need this?
	private int[] matchIndex;

	// Think this is done !!!!!!!!!
	public void go() {
		synchronized (mLock) {
			// Set this to current term in the case that it switched from another
			int term = mConfig.getCurrentTerm();
			myCurrentTimer = scheduleTimer(HEARTBEAT_INTERVAL, mID);

			nextIndex = new int[mConfig.getNumServers() + 1];
			for (int server = 1; server <= mConfig.getNumServers(); server++) {
				nextIndex[server] = mLog.getLastIndex() + 1;
			}

			// int term = 0;
			System.out.println("S" + mID + "." + term + ": switched to leader mode.");
			testPrint("L: S" + mID + "." + term + ": switched to leader mode.");

			// TODO: Added this dont know if we need it
			RaftResponses.setTerm(term);
			RaftResponses.clearAppendResponses(term);
			// Send Initial Heartbeats
			for (int i = 1; i <= mConfig.getNumServers(); i++) {
				// This should keep us from voting for ourselves
				if (i == mID)
					continue;

				remoteAppendEntries(i, mConfig.getCurrentTerm(), mID, nextIndex[i] - 1, mLog.getLastTerm(), new Entry[0], mCommitIndex);
			}
		}
	}

	// @param candidate’s term
	// @param candidate requesting vote
	// @param index of candidate’s last log entry
	// @param term of candidate’s last log entry
	// @return 0, if server votes for candidate; otherwise, server's
	// current term
	// worked on by: Molly

	// Think this is done!!!!!!!!!!!!
	public int requestVote(int candidateTerm, int candidateID, int lastLogIndex, int lastLogTerm) {
		synchronized (mLock) {
			int term = mConfig.getCurrentTerm();

			if (term < candidateTerm) { // if their term greater, they are real leader. I become a follower
				testPrint("L: S" + mID + "." + term + ": reverted to follower mode");
				myCurrentTimer.cancel();
				mConfig.setCurrentTerm(candidateTerm,0);
				RaftResponses.clearAppendResponses(term);
				// =========== Brian - Added this for consistency
				FollowerMode follower = new FollowerMode();
				RaftServerImpl.setMode(follower);
				return follower.requestVote(candidateTerm, candidateID, lastLogIndex, lastLogTerm);
			}
			return term;
		}
	}

	// @param leader’s term
	// @param current leader
	// @param index of log entry before entries to append
	// @param term of log entry before entries to append
	// @param entries to append (in order of 0 to append.length-1)
	// @param index of highest committed entry
	// @return 0, if server appended entries; otherwise, server's
	// current term

	// Think this is done!!!!!!!
	public int appendEntries(int leaderTerm, int leaderID, int prevLogIndex, int prevLogTerm, Entry[] entries,
			int leaderCommit) {
		synchronized (mLock) {
			
			
			int term = mConfig.getCurrentTerm();
			
			
			// TODO: Check if this is greater than or equal to
			if (leaderTerm > term) {
				mConfig.setCurrentTerm(leaderTerm, 0);
				myCurrentTimer.cancel();
				RaftResponses.clearAppendResponses(term);
				// ============= Brian - For consistency
				FollowerMode follower = new FollowerMode();
				RaftServerImpl.setMode(follower);
				return follower.appendEntries(leaderTerm, leaderID, prevLogIndex, prevLogTerm, entries, leaderCommit);
			}

			return term;
		}
	}

	// @param id of the timer that timed out

    // Think this is done !!!!!!!!!!
//	public void handleTimeout(int timerID) {
//		synchronized (mLock) {
//			myCurrentTimer.cancel();
//			int term = mConfig.getCurrentTerm();
//			int[] myResponses = RaftResponses.getAppendResponses(term);
//			myResponses = myResponses.clone();
//
//
//			testPrint("L: S" + mID + "." + term + "timeout, current entries: " + Arrays.toString(getEntries()) + " resp: " + Arrays.toString(myResponses));
//
//			// ============ Brian - Moved this here to not clearAppendResponses right after sending them out
//			myCurrentTimer = scheduleTimer(HEARTBEAT_INTERVAL, mID);
//			RaftResponses.clearAppendResponses(term);
//			testPrint("L: S" + mID + "." + term + "timeout, current entries: " + Arrays.toString(getEntries()) + " resp: " + Arrays.toString(myResponses));
//
//			for (int server = 1; server <= mConfig.getNumServers(); server++) {
//				if (server == mID)
//					continue;
//				// TODO: Check this with TA
//				// Brian - I added this to revert leader if it hears higher term RPC response
//				if (myResponses[server] > term){
//					mConfig.setCurrentTerm(myResponses[server], 0);
//					RaftResponses.clearAppendResponses(term);
//					RaftServerImpl.setMode(new FollowerMode());
//					return;
//				}
//				else if (myResponses[server] > 0) {
//					nextIndex[server]--;
//				}
//				else if (myResponses[server] == 0){
//					nextIndex[server] = mLog.getLastIndex() + 1;
//				}
//				// TODO: Check this added it to make sure nextIndex is updated if successful
//				int entryIter = 0;
//				Entry[] newEntries = new Entry[mLog.getLastIndex() + 1 - nextIndex[server]];
//				for (int iter = nextIndex[server]; iter <= mLog.getLastIndex(); iter++) {
//					newEntries[entryIter] = mLog.getEntry(iter);
//					entryIter++;
//				}
//
//				// TODO: Check with TA but added the -1 to indicate the one before where they will be added following Fig 2
//				Entry lastEntry = mLog.getEntry(nextIndex[server] - 1);
//
//				testPrint("L: S" + mID + "." + term + "timeout, index of last entry" + (nextIndex[server] - 1));
//				// TODO: lastEntry is sometimes null causing the following exception
//				//
//				//				Exception in thread "Timer-5" java.lang.NullPointerException
//				//				at edu.duke.raft.LeaderMode.handleTimeout(LeaderMode.java:134)
//				//				at edu.duke.raft.RaftMode$1.run(RaftMode.java:66)
//				//				at java.base/java.util.TimerThread.mainLoop(Timer.java:556)
//				//				at java.base/java.util.TimerThread.run(Timer.java:506)
//
//				int lastEntryTerm;
//				if (nextIndex[server] - 1 < 0){
//					lastEntryTerm = 0;
//				}
//				else {
//					lastEntryTerm = lastEntry.term;
//				}
//
//				remoteAppendEntries(server, mConfig.getCurrentTerm(), mID, nextIndex[server] - 1, lastEntryTerm, newEntries,
//						mCommitIndex);
//			}
//		}
//	}

	public void handleTimeout(int timerID) {
		synchronized (mLock) {
			myCurrentTimer.cancel();
			int term = mConfig.getCurrentTerm();
			int[] myResponses = RaftResponses.getAppendResponses(term);
			myResponses = myResponses.clone();


			testPrint("L: S" + mID + "." + term + "timeout, current entries: " + Arrays.toString(getEntries()) + " resp: " + Arrays.toString(myResponses));

			// ============ Brian - Moved this here to not clearAppendResponses right after sending them out
			myCurrentTimer = scheduleTimer(HEARTBEAT_INTERVAL, mID);
			RaftResponses.clearAppendResponses(term);
			testPrint("L: S" + mID + "." + term + "timeout, current entries: " + Arrays.toString(getEntries()) + " resp: " + Arrays.toString(myResponses));

			for (int server = 1; server <= mConfig.getNumServers(); server++) {
				if (server == mID)
					continue;
				// TODO: Check this with TA
				// Brian - I added this to revert leader if it hears higher term RPC response
				if (myResponses[server] > term){
					mConfig.setCurrentTerm(myResponses[server], 0);
					RaftResponses.clearAppendResponses(term);
					RaftServerImpl.setMode(new FollowerMode());
					return;
				}
//				else if (myResponses[server] > 0) {
//					nextIndex[server]--;
//				}
//				else if (myResponses[server] == 0){
//					nextIndex[server] = mLog.getLastIndex() + 1;
//				}
				// TODO: Check this added it to make sure nextIndex is updated if successful
				nextIndex[server] = 0;
				int entryIter = 0;
				Entry[] newEntries = new Entry[mLog.getLastIndex() + 1 - nextIndex[server]];
				for (int iter = nextIndex[server]; iter <= mLog.getLastIndex(); iter++) {
					newEntries[entryIter] = mLog.getEntry(iter);
					entryIter++;
				}

				// TODO: Check with TA but added the -1 to indicate the one before where they will be added following Fig 2
//				Entry lastEntry = mLog.getEntry(nextIndex[server] - 1);

				testPrint("L: S" + mID + "." + term + "timeout, index of last entry" + (nextIndex[server] - 1));
				// TODO: lastEntry is sometimes null causing the following exception
				//
				//				Exception in thread "Timer-5" java.lang.NullPointerException
				//				at edu.duke.raft.LeaderMode.handleTimeout(LeaderMode.java:134)
				//				at edu.duke.raft.RaftMode$1.run(RaftMode.java:66)
				//				at java.base/java.util.TimerThread.mainLoop(Timer.java:556)
				//				at java.base/java.util.TimerThread.run(Timer.java:506)

				int lastEntryTerm;
//				if (nextIndex[server] - 1 < 0){
					lastEntryTerm = 0;
//				}
//				else {
//					lastEntryTerm = lastEntry.term;
//				}

				remoteAppendEntries(server, mConfig.getCurrentTerm(), mID, nextIndex[server] - 1, lastEntryTerm, newEntries,
						mCommitIndex);
			}
		}
	}

	private void testPrint(String s) {
//		System.out.println(s);
	}
	
	private Entry[] getEntries() {
		if (mLog.getLastIndex() == -1) return new Entry[0];
		
		Entry[] myEntries = new Entry[mLog.getLastIndex() + 1];
		for (int i = 0; i <= mLog.getLastIndex(); i++) {
			myEntries[i] = mLog.getEntry(i);
		}
		return myEntries;
	}
}
