package net.bananemdnsa.historystages.data.lock;

import com.google.gson.annotations.SerializedName;

/** How a locked slot shows itself: gone entirely, or still there with a lock on top. */
public enum SlotLockMode {
    @SerializedName("hide") HIDE,
    @SerializedName("overlay") OVERLAY
}
