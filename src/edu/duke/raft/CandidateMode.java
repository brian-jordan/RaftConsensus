package edu.duke.raft;

import java.util.Arrays;
import java.util.Timer;

public class CandidateMode extends RaftMode {
	private Timer myCurrentTimer;
	private Timer myCurrentTimerMoreFreq;
	
	private int MORE_FREQ_TIMEOUT = ELECTION_TIMEOUT_MIN / 2;

	// Think this is done !!!!!!!!!
	public void go() {
		synchronized (mLock) {
			// Increment term when starting election
			mConfig.setCurrentTerm(mConfig.getCurrentTerm() + 1, mID);
			int term = mConfig.getCurrentTerm();
			System.out.println("S" + mID + "." + term + ": switched to candidate mode.");
			testPrint("C: S" + mID + "." + term + ": go switched to candidate mode.");

//			long randomTime = mConfig.getTimeoutOverride() == -1 ?
//					((long) ((Math.random() * (ELECTION_TIMEOUT_MAX - ELECTION_TIMEOUT_MIN + 100))
//					+ ELECTION_TIMEOUT_MIN)) : mConfig.getTimeoutOverride();
			long randomTime = ((long) ((Math.random() * (ELECTION_TIMEOUT_MAX - ELECTION_TIMEOUT_MIN)) + ELECTION_TIMEOUT_MIN));
			if (mConfig.getTimeoutOverride() != -1) {
				randomTime = mConfig.getTimeoutOverride();
			}
			testPrint("C: time " + randomTime);
			myCurrentTimer = scheduleTimer(randomTime, mID);
			myCurrentTimerMoreFreq = scheduleTimer(MORE_FREQ_TIMEOUT, 0);

			//myCurrentTimer = scheduleTimer(ELECTION_TIMEOUT_MAX, mID);

			// Start an election:
			RaftResponses.setTerm(term);

			// =========== Brian - Stopped clearing votes because this would make them all -1 for same election
			RaftResponses.clearVotes(term);
			requestVotes(term);

		}
	}

	// Think this is done !!!!!!!!!!1
	private void requestVotes(int term) {
		
		for (int i = 1; i <= mConfig.getNumServers(); i++) {
			// This should keep us from voiting for ourselves
			if (i == mID)
				continue;
			testPrint("C: S" + mID + "." + term + " is requesting vote from S" + i);
			remoteRequestVote(i, term, mID, mLog.getLastIndex(), mLog.getLastTerm());
		}
	}

	// @param candidate’s term
	// @param candidate requesting vote
	// @param index of candidate’s last log entry
	// @param term of candidate’s last log entry
	// @return 0, if server votes for candidate; otherwise, server's
	// current term

	// Think this is done !!!!!!!!!!1
	public int requestVote(int candidateTerm, int candidateID, int lastLogIndex, int lastLogTerm) {
		synchronized (mLock) {
			int term = mConfig.getCurrentTerm();
			testPrint("C: S" + mID + "." + term + ": requestVote, received vote request from S" + candidateID + ".");
			
			// This guards against our own potential idiocy, if we ever
			// accidentally vote for ourselves during an election!
			if (candidateID == mID) {
				testPrint("C: S" + mID + "." + term + ": BUG! votied for self");
				return 0;
			}
			

			// If my term is greater or equal, reject
			// ============= Brian - Changed this to greater than or equal to
			if (term >= candidateTerm) {
				testPrint("C: S" + mID + "." + term + ": requestVote, deny vote from S" + candidateID + "." + candidateTerm);
				return term;
			} else { // If my term is less, revert to follower
				testPrint("C: S" + mID + "." + term + "requestVote, revert to follower mode");
				// ========== Brian - Don't think we need to do this twice
			//	mConfig.setCurrentTerm(candidateTerm, 0);
				myCurrentTimer.cancel();
				myCurrentTimerMoreFreq.cancel();

				// ============= Brian - Here we are trying to get them to vote in the same election so we can't clear votes
				RaftResponses.clearVotes(term);
				FollowerMode follower = new FollowerMode();
				RaftServerImpl.setMode(follower);
				// ============= Brian - I think this will now work
				return follower.requestVote(candidateTerm, candidateID, lastLogIndex, lastLogTerm);
			}
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

	// This is done !!!!!!!!!!!!
	public int appendEntries(int leaderTerm, int leaderID, int prevLogIndex, int prevLogTerm, Entry[] entries,
			int leaderCommit) {
		synchronized (mLock) {
			int term = mConfig.getCurrentTerm();


			if (leaderTerm >= term) {
				// ===== Brian - removed this for consistency
			//	mConfig.setCurrentTerm(leaderTerm, 0);
				myCurrentTimer.cancel();
				myCurrentTimerMoreFreq.cancel();
				RaftResponses.clearVotes(term);
				// ============= Brian - added this to not waste a call
				FollowerMode follower = new FollowerMode();
				RaftServerImpl.setMode(follower);
				return follower.appendEntries(leaderTerm, leaderID, prevLogIndex, prevLogTerm, entries, leaderCommit);
			}

			return term;
		}
	}

	// @param id of the timer that timed out

	// Think this is done !!!!!!!!!!

	public void handleTimeout(int timerID) {
		synchronized (mLock) {

			//myCurrentTimer.cancel();

			int term = mConfig.getCurrentTerm();
			int numServers = mConfig.getNumServers();
			int[] votes = RaftResponses.getVotes(term);
			
			testPrint("C: S" + mID + "." + term +  ": timeout, current votes: " + Arrays.toString(votes));

			// This election is out of date -> go to follower mode
			if (votes == null) {
				RaftResponses.clearVotes(term);
				testPrint("C: S" + mID + "." + term +  ": timeout, null votes switching to follower ");
				myCurrentTimer.cancel();
				myCurrentTimerMoreFreq.cancel();
				RaftServerImpl.setMode(new FollowerMode());
				return;
			}

			int votesFor = 1;

			for (int i = 1; i < votes.length; i++) {
				if (i == mID)
					continue;
				if (votes[i] == 0) {
					votesFor++;
				}
				// ========== Brian - made this or equal to so i think this will work
				else if (votes[i] >= term){
					RaftResponses.clearVotes(term);
					testPrint("C: S" + mID + "." + term +  ": timeout, someone is ahead switching to follower ");
					myCurrentTimer.cancel();
					myCurrentTimerMoreFreq.cancel();
					RaftServerImpl.setMode(new FollowerMode());
					return;
				}
			}

			// Won the election -> become leader
			if (votesFor > numServers / 2.0) {
				testPrint("C: S" + mID + "." + term + "timeout,  wins election!");
				RaftResponses.clearVotes(term);
				myCurrentTimer.cancel();
				myCurrentTimerMoreFreq.cancel();
				RaftServerImpl.setMode(new LeaderMode());
				return;
			}
			// Didnt win -> stay candidate
			// Brian - This goes against sudo code but TA said to do this and if receives
			// heartbeat go back to follower
			else {

				if (timerID == 0){
					myCurrentTimerMoreFreq.cancel();
					myCurrentTimerMoreFreq = scheduleTimer(MORE_FREQ_TIMEOUT, 0);
				}
				else {
					RaftResponses.clearVotes(term);
					testPrint("C: S" + mID + "." + term + "timeout,  didn't win... reverting back to candidate!");
					myCurrentTimerMoreFreq.cancel();
					myCurrentTimer.cancel();
					go();
				}

			}
		}

	}

	private void testPrint(String s) {
//		System.out.println(s);
	}
}
