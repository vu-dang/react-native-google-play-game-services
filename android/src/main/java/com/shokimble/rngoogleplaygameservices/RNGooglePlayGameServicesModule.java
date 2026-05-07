
package com.shokimble.rngoogleplaygameservices;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.BaseActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;

import android.util.Log;
import android.app.Activity;
import android.content.Intent;
import androidx.annotation.NonNull;

import com.google.android.gms.games.AuthenticationResult;
import com.google.android.gms.games.AchievementsClient;
import com.google.android.gms.games.AnnotatedData;
import com.google.android.gms.games.GamesSignInClient;
import com.google.android.gms.games.LeaderboardsClient;
import com.google.android.gms.games.PlayGames;
import com.google.android.gms.games.PlayGamesSdk;
import com.google.android.gms.games.PlayersClient;
import com.google.android.gms.games.SnapshotsClient;
import com.google.android.gms.games.SnapshotsClient.DataOrConflict;
import com.google.android.gms.games.SnapshotsClient.SnapshotConflict;
import com.google.android.gms.games.leaderboard.LeaderboardScore;
import com.google.android.gms.games.leaderboard.LeaderboardVariant;
import com.google.android.gms.games.snapshot.Snapshot;
import com.google.android.gms.games.snapshot.SnapshotMetadataChange;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

public class RNGooglePlayGameServicesModule extends ReactContextBaseJavaModule {

  private final ReactApplicationContext reactContext;

  private AchievementsClient mAchievementsClient;
  private LeaderboardsClient mLeaderboardsClient;
  private PlayersClient mPlayersClient;
  private SnapshotsClient mSnapshotsClient;
  private Promise achievementPromise;
  private Promise leaderboardPromise;
  private Snapshot workingSnapshot;

  private static final int RC_ACHIEVEMENT_UI = 9003;
  private static final int RC_LEADERBOARD_UI = 9004;

  private static final String TAG = "shorngames";

  /////////////////////////////////////////////////////////////////////////////

  private final ActivityEventListener mActivityEventListener = new BaseActivityEventListener() {

    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent intent) {
      super.onActivityResult(activity, requestCode, resultCode, intent);

      if (requestCode == RC_ACHIEVEMENT_UI) {
        if (achievementPromise == null) return;
        achievementPromise.resolve("Achievement dialog complete");
        return;
      }

      if (requestCode == RC_LEADERBOARD_UI) {
        if (leaderboardPromise == null) return;
        leaderboardPromise.resolve("Leaderboard dialog complete");
      }
    }

  };

  /////////////////////////////////////////////////////////////////////////////

  public RNGooglePlayGameServicesModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
    reactContext.addActivityEventListener(mActivityEventListener);
    PlayGamesSdk.initialize(reactContext.getApplicationContext());
  }

  /////////////////////////////////////////////////////////////////////////////

  @Override
  public String getName() {
    return "RNGooglePlayGameServices";
  }

  /////////////////////////////////////////////////////////////////////////////

  @ReactMethod
  public void isSignedIn(final Promise promise) {
    Activity activity = getCurrentActivity();
    if (activity == null) {
      promise.reject("No activity");
      return;
    }
    PlayGames.getGamesSignInClient(activity).isAuthenticated()
      .addOnCompleteListener(task -> {
        if (task.isSuccessful() && task.getResult().isAuthenticated()) {
          promise.resolve("signed in");
        } else {
          promise.reject("not signed in");
        }
      });
  }

  /////////////////////////////////////////////////////////////////////////////

  @ReactMethod
  public void signInSilently(final Promise promise) {
    Log.d(TAG, "signInSilently()");
    Activity activity = getCurrentActivity();
    if (activity == null) {
      if (promise != null) promise.reject("No activity");
      return;
    }
    PlayGames.getGamesSignInClient(activity).signIn()
      .addOnCompleteListener(task -> {
        if (task.isSuccessful() && task.getResult().isAuthenticated()) {
          Log.d(TAG, "signInSilently(): success");
          onConnected();
          if (promise != null) promise.resolve("silent sign in successful");
        } else {
          Log.d(TAG, "signInSilently(): failure");
          onDisconnected();
          if (promise != null) promise.reject("silent sign in failed");
        }
      });
  }

  /////////////////////////////////////////////////////////////////////////////

  @ReactMethod
  public void signInIntent(final Promise promise) {
    // Play Games Services v2 handles sign-in automatically; no separate intent flow.
    signInSilently(promise);
  }

  /////////////////////////////////////////////////////////////////////////////

  @ReactMethod
  public void signOut(final Promise promise) {
    // Sign-out is not supported in Play Games Services v2.
    // Account management is handled through device OS settings.
    onDisconnected();
    promise.resolve("signed out");
  }

  /////////////////////////////////////////////////////////////////////////////

  @ReactMethod
  public void revealAchievement(String id, final Promise promise) {
    if (mAchievementsClient == null) {
      promise.reject("Please sign in first");
      return;
    }
    mAchievementsClient.reveal(id);
    promise.resolve("Revealed achievement");
  }

  /////////////////////////////////////////////////////////////////////////////

  @ReactMethod
  public void getUserId(final Promise promise) {
    if (mPlayersClient == null) {
      promise.reject("Please sign in first");
      return;
    }
    mPlayersClient.getCurrentPlayerId()
      .addOnCompleteListener(task -> {
        if (task.isSuccessful()) {
          promise.resolve(task.getResult());
        } else {
          promise.reject("Get ID failed");
        }
      });
  }

  /////////////////////////////////////////////////////////////////////////////

  @ReactMethod
  public void unlockAchievement(String id, final Promise promise) {
    if (mAchievementsClient == null) {
      promise.reject("Please sign in first");
      return;
    }
    mAchievementsClient.unlock(id);
    promise.resolve("Unlocked achievement");
  }

  /////////////////////////////////////////////////////////////////////////////

  @ReactMethod
  public void incrementAchievement(String id, int inc, final Promise promise) {
    if (mAchievementsClient == null) {
      promise.reject("Please sign in first");
      return;
    }
    mAchievementsClient.increment(id, inc);
    promise.resolve("Incremented achievement");
  }

  /////////////////////////////////////////////////////////////////////////////

  @ReactMethod
  public void setAchievementSteps(String id, int steps, final Promise promise) {
    if (mAchievementsClient == null) {
      promise.reject("Please sign in first");
      return;
    }
    mAchievementsClient.setSteps(id, steps);
    promise.resolve("Achievement steps set");
  }

  /////////////////////////////////////////////////////////////////////////////

  @ReactMethod
  public void discardAndCloseSnapshot(final Promise promise) {
    if (mSnapshotsClient == null) {
      promise.reject("Please sign in first");
      return;
    }
    if (workingSnapshot == null) {
      promise.resolve(null);
      return;
    }
    mSnapshotsClient.discardAndClose(workingSnapshot);
    workingSnapshot = null;
    promise.resolve(null);
  }

  /////////////////////////////////////////////////////////////////////////////

  @ReactMethod
  public void commitAndCloseSnapshot(String data, String description, final Promise promise) {
    if (mSnapshotsClient == null) {
      promise.reject("Please sign in first");
      return;
    }
    if (workingSnapshot == null) {
      promise.resolve(null);
      return;
    }
    try {
      SnapshotMetadataChange.Builder mc = new SnapshotMetadataChange.Builder();
      mc.fromMetadata(workingSnapshot.getMetadata());
      mc.setDescription(description);
      workingSnapshot.getSnapshotContents().writeBytes(data.getBytes());
      mSnapshotsClient.commitAndClose(workingSnapshot, mc.build());
      workingSnapshot = null;
      promise.resolve(null);
    } catch (Exception e) {
      promise.reject("Error committing Snapshot: " + e.getMessage());
    }
  }

  /////////////////////////////////////////////////////////////////////////////

  @ReactMethod
  public void loadSnapshot(String name, final Promise promise) {
    if (mSnapshotsClient == null) {
      promise.reject("Please sign in first");
      return;
    }

    mSnapshotsClient.open(name, true, SnapshotsClient.RESOLUTION_POLICY_MANUAL)
      .addOnSuccessListener(new OnSuccessListener<DataOrConflict<Snapshot>>() {
        @Override
        public void onSuccess(DataOrConflict<Snapshot> result) {
          if (!result.isConflict()) {
            try {
              workingSnapshot = result.getData();
              WritableMap map = Arguments.createMap();
              map.putBoolean("isConflict", false);
              map.putString("data", new String(workingSnapshot.getSnapshotContents().readFully(), "UTF-8"));
              promise.resolve(map);
            } catch (Exception e) {
              workingSnapshot = null;
              promise.reject("Error reading snapshot!");
            }
            return;
          }
          try {
            SnapshotConflict conflict = result.getConflict();
            workingSnapshot = conflict.getSnapshot();
            Snapshot conflictSnapshot = conflict.getConflictingSnapshot();
            mSnapshotsClient.resolveConflict(conflict.getConflictId(), workingSnapshot);
            WritableMap map = Arguments.createMap();
            map.putBoolean("isConflict", true);
            map.putString("data", new String(workingSnapshot.getSnapshotContents().readFully(), "UTF-8"));
            map.putString("conflictData", new String(conflictSnapshot.getSnapshotContents().readFully(), "UTF-8"));
            promise.resolve(map);
          } catch (Exception e) {
            workingSnapshot = null;
            promise.reject("Error reading snapshot!");
          }
        }
      })
      .addOnFailureListener(new OnFailureListener() {
        @Override
        public void onFailure(@NonNull Exception e) {
          promise.reject("LoadSnapshot: FAILURE - " + e.getMessage());
        }
      });
  }

  /////////////////////////////////////////////////////////////////////////////

  @ReactMethod
  public void setLeaderboardScore(String id, int score, final Promise promise) {
    if (mLeaderboardsClient == null) {
      promise.reject("Please sign in first");
      return;
    }
    mLeaderboardsClient.submitScore(id, score);
    promise.resolve("Leaderboard score set");
  }

  /////////////////////////////////////////////////////////////////////////////

  @ReactMethod
  public void getLeaderboardScore(String id, final Promise promise) {
    if (mLeaderboardsClient == null) {
      promise.reject("Please sign in first");
      return;
    }
    mLeaderboardsClient.loadCurrentPlayerLeaderboardScore(id, LeaderboardVariant.TIME_SPAN_ALL_TIME, LeaderboardVariant.COLLECTION_PUBLIC)
      .addOnSuccessListener(new OnSuccessListener<AnnotatedData<LeaderboardScore>>() {
        @Override
        public void onSuccess(AnnotatedData<LeaderboardScore> leaderboardScoreAnnotatedData) {
          if (leaderboardScoreAnnotatedData == null || leaderboardScoreAnnotatedData.get() == null) {
            promise.resolve(null);
            return;
          }
          promise.resolve("" + leaderboardScoreAnnotatedData.get().getRawScore());
        }
      })
      .addOnFailureListener(new OnFailureListener() {
        @Override
        public void onFailure(@NonNull Exception e) {
          promise.reject("LeaderBoard: FAILURE");
        }
      });
  }

  /////////////////////////////////////////////////////////////////////////////

  @ReactMethod
  public void showAchievements(final Promise promise) {
    if (mAchievementsClient == null) {
      promise.reject("Please sign in first");
      return;
    }

    achievementPromise = promise;

    mAchievementsClient.getAchievementsIntent()
      .addOnSuccessListener(new OnSuccessListener<Intent>() {
        @Override
        public void onSuccess(Intent intent) {
          getCurrentActivity().startActivityForResult(intent, RC_ACHIEVEMENT_UI);
        }
      })
      .addOnFailureListener(new OnFailureListener() {
        @Override
        public void onFailure(@NonNull Exception e) {
          promise.reject("Could not launch achievements intent");
        }
      });
  }

  /////////////////////////////////////////////////////////////////////////////

  @ReactMethod
  public void showAllLeaderboards(final Promise promise) {
    if (mLeaderboardsClient == null) {
      promise.reject("Please sign in first");
      return;
    }

    leaderboardPromise = promise;

    mLeaderboardsClient.getAllLeaderboardsIntent()
      .addOnSuccessListener(new OnSuccessListener<Intent>() {
        @Override
        public void onSuccess(Intent intent) {
          getCurrentActivity().startActivityForResult(intent, RC_LEADERBOARD_UI);
        }
      })
      .addOnFailureListener(new OnFailureListener() {
        @Override
        public void onFailure(@NonNull Exception e) {
          promise.reject("Could not launch leaderboards intent");
        }
      });
  }

  /////////////////////////////////////////////////////////////////////////////

  @ReactMethod
  public void showLeaderboard(String id, final Promise promise) {
    if (mLeaderboardsClient == null) {
      promise.reject("Please sign in first");
      return;
    }

    leaderboardPromise = promise;

    mLeaderboardsClient.getLeaderboardIntent(id)
      .addOnSuccessListener(new OnSuccessListener<Intent>() {
        @Override
        public void onSuccess(Intent intent) {
          getCurrentActivity().startActivityForResult(intent, RC_LEADERBOARD_UI);
        }
      })
      .addOnFailureListener(new OnFailureListener() {
        @Override
        public void onFailure(@NonNull Exception e) {
          promise.reject("Could not launch leaderboard intent");
        }
      });
  }

  /////////////////////////////////////////////////////////////////////////////

  private void onConnected() {
    Log.d(TAG, "onConnected(): connected to Google Play Games");
    Activity activity = getCurrentActivity();
    mAchievementsClient = PlayGames.getAchievementsClient(activity);
    mLeaderboardsClient = PlayGames.getLeaderboardsClient(activity);
    mPlayersClient = PlayGames.getPlayersClient(activity);
    mSnapshotsClient = PlayGames.getSnapshotsClient(activity);
  }

  /////////////////////////////////////////////////////////////////////////////

  private void onDisconnected() {
    Log.d(TAG, "onDisconnected()");
    mAchievementsClient = null;
    mLeaderboardsClient = null;
    mPlayersClient = null;
    mSnapshotsClient = null;
  }

  /////////////////////////////////////////////////////////////////////////////

}
