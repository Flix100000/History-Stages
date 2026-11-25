package net.bananemdnsa.historystages.data.lock;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

/**
 * Plants a stage keeps from growing: block ids and {@code #block_tag} entries.
 *
 * <p>{@code mod_linked} is editor bookkeeping, the same as for biomes: it records which entries
 * came from the mod popup so unticking them there can drop them again. It is never matched.
 */
public class CropLocks {
    private List<String> crops;

    @SerializedName("mod_linked")
    private List<String> modLinked;

    public CropLocks() {
        this.crops = new ArrayList<>();
        this.modLinked = new ArrayList<>();
    }

    public List<String> getCrops() {
        return crops != null ? crops : new ArrayList<>();
    }

    public List<String> getModLinked() {
        return modLinked != null ? modLinked : new ArrayList<>();
    }

    public void setCrops(List<String> crops) {
        this.crops = crops != null ? new ArrayList<>(crops) : new ArrayList<>();
    }

    public void setModLinked(List<String> modLinked) {
        this.modLinked = modLinked != null ? new ArrayList<>(modLinked) : new ArrayList<>();
    }

    public boolean isEmpty() {
        return getCrops().isEmpty() && getModLinked().isEmpty();
    }
}
