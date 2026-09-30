package net.bananemdnsa.historystages.screen;

import net.bananemdnsa.historystages.client.display.BlockedLines;
import java.util.List;
import net.bananemdnsa.historystages.api.dependency.Requirement;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.block.TieredPedestal;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.dependency.ItemTagResolution;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageMode;
import net.bananemdnsa.historystages.api.dependency.RequirementResult;
import net.bananemdnsa.historystages.init.ModItems;
import net.bananemdnsa.historystages.network.serverbound.CheckDependencyPacket;
import net.bananemdnsa.historystages.network.serverbound.DepositDependencyPacket;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.research.BoosterUtil;
import net.bananemdnsa.historystages.research.TierMatcher;
import net.bananemdnsa.historystages.client.cache.ClientStageCache;
import net.bananemdnsa.historystages.client.cache.ClientIndividualStageCache;
import net.bananemdnsa.historystages.client.cache.ClientDependencyCache;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class ResearchPedestalScreen extends AbstractContainerScreen<ResearchPedestalMenu> {
    private static final ResourceLocation[] TIER_TEXTURES = {
            ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "textures/gui/pedestal/gui_t1.png"),
            ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "textures/gui/pedestal/gui_t2.png"),
            ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "textures/gui/pedestal/gui_t3.png"),
            ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "textures/gui/pedestal/gui_t4.png"),
    };
    private static final ResourceLocation TEXTURE_BAR =
            ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "textures/gui/pedestal/bar.png");
    private static final ResourceLocation TEXTURE_DEP =
            ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "textures/gui/pedestal/dep_gui.png");
    private static final ResourceLocation TEXTURE_BUTTONS =
            ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "textures/gui/pedestal/buttons.png");

    // Two palettes, because the screen has two backgrounds. The main labels sit on the dark
    // slab and have to be light; the dependency scroll is cream parchment and keeps dark ink.
    // Kept close to neutral on purpose — green marks progress, red marks a problem, and
    // nothing else carries colour.

    /**
     * On the pedestal itself. Drawn with a shadow, because the band behind these labels is
     * dark slab in tiers 1-3 and bright gold cloth in tier 4 — no flat colour reads on both,
     * a shadowed one does. Warm rather than neutral, so the labels belong to the same set as
     * the parchment strip and the scroll; the only real colour left means something, green
     * for finishing and red for a problem.
     */
    private static final int SLAB_PRIMARY   = 0xEDE3C8; // Stage line — parchment cream
    private static final int SLAB_SECONDARY = 0xBFB49B; // Time, multiplier, idle states
    private static final int SLAB_ACCENT    = 0x5CE65C; // Finalizing
    private static final int SLAB_ERROR     = 0xFF6B6B; // Wrong tier, missing requirements, already learned

    /** On the cream dependency scroll — dark ink, unchanged from before the re-skin. */
    private static final int PANEL_PRIMARY   = 0x404040; // Requirement names
    private static final int PANEL_SECONDARY = 0x707070; // Loading, cost reduction
    private static final int PANEL_ACCENT    = 0x2E8B57; // A requirement that is satisfied
    private static final int PANEL_ERROR     = 0xAA3333; // Already learned

    /** Breathing room between two labels sharing a line. */
    private static final int LINE_GAP = 6;

    /**
     * Depth for a wash laid over a slot's item. A container screen stacks two lifts before the
     * icon is drawn — {@code renderSlot} pushes +100 and {@code renderItem} another +150, with
     * the stack count another +200 above that — and the depth test is back on by the time a
     * screen paints over {@code super.render}. Anything below ~300 loses to the item it covers,
     * which is how a grey plane ends up behind the thing it is meant to hide.
     */
    private static final int SLOT_OVERLAY_Z = 350;

    private boolean hasDependencies = false;
    private Component pendingTooltip = null;
    /** Multi-line tooltip for the "blocked" status line; wins over {@link #pendingTooltip}. */
    private List<Component> pendingTooltipLines = null;

    // Scrolling state
    private float scrollAmount = 0.0f;
    private int totalContentHeight = 0;
    private CompoundTag lastDepositedNBT = null;
    private long lastDependencyCheck = 0;

    public ResearchPedestalScreen(ResearchPedestalMenu pMenu, Inventory pPlayerInventory, Component pTitle) {
        super(pMenu, pPlayerInventory, pTitle);
    }

    /** The sheet for the pedestal this screen belongs to. Reads the block state, which the
     *  client already has, so no extra sync is needed. */
    private ResourceLocation tierTexture() {
        int tier = 1;
        var be = this.menu.getBlockEntity();
        if (be != null && be.getBlockState().getBlock() instanceof TieredPedestal tiered) {
            tier = tiered.getTier();
        }
        return TIER_TEXTURES[PedestalLayout.clampTier(tier) - 1];
    }

    /**
     * Whether the button does anything if pressed right now. Mirrors the conditions
     * {@code ResearchPedestalBlockEntity.tryStart} enforces server-side — this is only the
     * display, the server still refuses on its own. Pausing a running research is always
     * allowed, so a running pedestal is never disabled.
     */
    private boolean canPressStart() {
        if (this.menu.isRunning()) return true;

        ItemStack stack = this.menu.getSlot(36).getItem();
        if (stack.isEmpty()) return false;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains("StageResearch")) return false;
        String stageId = tag.getString("StageResearch");
        if (ModItems.CREATIVE_STAGE_ID.equals(stageId)) return true;

        boolean individual = StageManager.isIndividualStage(stageId);
        StageEntry entry = individual
                ? StageManager.getIndividualStages().get(stageId)
                : StageManager.getStages().get(stageId);
        // Only DEFAULT stages research here; AUTO/EXTERNAL/TEMPORARY never do.
        if (entry == null || entry.getMode() != StageMode.DEFAULT) return false;

        boolean unlocked = individual
                ? ClientIndividualStageCache.isStageUnlocked(stageId)
                : ClientStageCache.isStageUnlocked(stageId);
        if (unlocked) return false;

        if (this.menu.data.get(7) == 1) return false;  // pedestal tier does not satisfy the stage
        return this.menu.data.get(4) == 1;             // requirements fulfilled
    }

    @Override
    protected void init() {
        // Determine dependency state before super.init() so imageWidth is set correctly
        checkDependencies();

        this.imageWidth = hasDependencies
                ? PedestalLayout.WIDTH + PedestalLayout.DEP_GAP + PedestalLayout.DEP_W
                : PedestalLayout.WIDTH;
        this.imageHeight = PedestalLayout.HEIGHT;

        super.init();

        // If we have dependencies, anchor leftPos so the MAIN panel area stays centered.
        // This keeps all slots at their expected screen positions.
        if (hasDependencies) {
            this.leftPos = (this.width - PedestalLayout.WIDTH) / 2;
        }

        this.addRenderableWidget(new PedestalIconButton(
                this.leftPos + PedestalLayout.BUTTON_X,
                this.topPos + PedestalLayout.BUTTON_Y,
                TEXTURE_BUTTONS,
                () -> this.menu.isRunning() ? PedestalLayout.ICON_COL_PAUSE : PedestalLayout.ICON_COL_PLAY,
                this::canPressStart,
                // No tooltip: a play/pause icon needs no caption.
                null,
                () -> net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new net.bananemdnsa.historystages.network.serverbound.PedestalControlPacket(
                                this.menu.getBlockPos(), !this.menu.isRunning()))));
    }

    private void checkDependencies() {
        ItemStack stack = menu.getSlot(36).getItem();
        hasDependencies = false;
        if (!stack.isEmpty()) {
            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (tag.contains("StageResearch")) {
                String stageId = tag.getString("StageResearch");
                if (!ModItems.CREATIVE_STAGE_ID.equals(stageId)) {
                    StageEntry entry = StageManager.isIndividualStage(stageId)
                            ? StageManager.getIndividualStages().get(stageId)
                            : StageManager.getStages().get(stageId);
                    if (entry != null && entry.hasDependencies()) {
                        hasDependencies = true;
                    }
                }
            }
        }
    }

    /**
     * Cuts {@code text} down to {@code maxWidth} pixels, marking the cut with an ellipsis.
     * Stage names come from pack authors and can be any length, and German runs longer than
     * English throughout, so nothing on this screen may assume its text fits.
     */
    private Component trimToWidth(Component text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) return text;
        int ellipsis = this.font.width("…");
        String cut = this.font.plainSubstrByWidth(text.getString(), Math.max(0, maxWidth - ellipsis));
        return Component.literal(cut + "…");
    }

    /** Draws {@code text} at the left margin. Shadowed — see the palette comment. */
    private void drawLeft(GuiGraphics guiGraphics, Component text, int y, int colour) {
        guiGraphics.drawString(this.font, text, PedestalLayout.TEXT_X, y, colour, true);
    }

    /** Draws {@code text} so its right edge lands on {@link PedestalLayout#TEXT_RIGHT}. */
    private void drawRightAligned(GuiGraphics guiGraphics, Component text, int y, int colour) {
        guiGraphics.drawString(this.font, text,
                PedestalLayout.TEXT_RIGHT - this.font.width(text), y, colour, true);
    }

    /** Draws {@code text} centred across the panel body. */
    private void drawCentred(GuiGraphics guiGraphics, Component text, int y, int colour) {
        guiGraphics.drawString(this.font, text,
                (PedestalLayout.WIDTH / 2) - (this.font.width(text) / 2), y, colour, true);
    }

    /**
     * Draws a line as "left label ... right label", cutting the right one down to whatever
     * the left leaves free. Both lines on this panel are built this way, and in German the
     * two halves together are wider than the panel more often than not.
     */
    private void drawSplitLine(GuiGraphics guiGraphics, int y,
                               Component left, int leftColour, Component right, int rightColour) {
        Component leftCut = trimToWidth(left, slabTextWidth());
        drawLeft(guiGraphics, leftCut, y, leftColour);
        if (right == null) return;
        int used = PedestalLayout.TEXT_X + this.font.width(leftCut) + LINE_GAP;
        drawRightAligned(guiGraphics, trimToWidth(right, PedestalLayout.TEXT_RIGHT - used),
                y, rightColour);
    }

    /** The width labels on the pedestal may use before they have to be cut. */
    private int slabTextWidth() {
        return PedestalLayout.TEXT_RIGHT - PedestalLayout.TEXT_X;
    }

    /**
     * Writes the percentage at the left end of the progress bar. Shadowed white: the fill
     * grows out from under it, so the same number sits over green at 20% and over the empty
     * rail at 0% and has to read on both.
     */
    private void drawBarPercent(GuiGraphics guiGraphics, int percent) {
        guiGraphics.drawString(this.font,
                Component.translatable("gui.historystages.pedestal.progress", percent),
                PedestalLayout.BAR_TEXT_X, PedestalLayout.BAR_TEXT_Y, SLAB_PRIMARY, true);
    }

    // --- dependency cards ---

    /** Height of one requirement card: two 8px lines plus padding. */
    private static final int CARD_H = 24;
    private static final int CARD_GAP = 2;
    private static final int CARD_PAD = 3;

    /** What kind of requirement this is, shown above the value. */
    private Component requirementKind(RequirementResult.EntryResult entry) {
        return Component.translatable("gui.historystages.pedestal.req." + entry.getType());
    }

    /**
     * The requirement itself. Items get a count instead of their description, because the
     * icon already says which item it is and the number is what the player is watching.
     */
    private Component requirementValue(RequirementResult.EntryResult entry) {
        if (isItemLike(entry)) {
            MutableComponent count = Component.literal(entry.getCurrent() + "/" + entry.getRequired());
            // A booster under the pedestal cuts the cost; show what it would have been.
            if (entry.getOriginalRequired() > 0 && entry.getOriginalRequired() != entry.getRequired()) {
                count.append(Component.literal(" (" + entry.getOriginalRequired() + ")")
                        .withStyle(ChatFormatting.GRAY));
            }
            return count;
        }
        return Component.literal(entry.getDescription());
    }

    /**
     * The full text for a card's hover tooltip: "Kind: value". For items the icon carries the
     * identity on the card, but a tooltip has no icon — so the item's name goes in here,
     * ahead of the count.
     */
    private Component requirementTooltip(RequirementResult.EntryResult entry) {
        MutableComponent value;
        if (isItemLike(entry)) {
            ItemStack icon = requirementIcon(entry);
            Component name = icon.isEmpty() ? Component.literal(entry.getId()) : icon.getHoverName();
            value = name.copy().append(Component.literal(" "))
                    .append(requirementValue(entry));
        } else {
            value = requirementValue(entry).copy();
        }
        return requirementKind(entry).copy().withStyle(ChatFormatting.GRAY)
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(value.withStyle(ChatFormatting.WHITE));
    }

    /**
     * Whether this card carries an item icon and shows a count instead of a description.
     *
     * <p>True for both item kinds. A tag card is an item card in every way the player can see —
     * the only difference is that its icon is still making up its mind.
     */
    private static boolean isItemLike(RequirementResult.EntryResult entry) {
        return "item".equals(entry.getType()) || "item_tag".equals(entry.getType());
    }

    /**
     * Whether the stack in the deposit slot is one this entry is waiting for.
     *
     * <p>Comparing ids is enough for a plain item and wrong for a tag: an unsettled tag has no
     * item id to compare against, and a settled one is identified by the item it chose, not by
     * the tag it still calls itself. Without this the little progress bar under the slot simply
     * never appeared for a tag, while the deposit went through — which reads as nothing
     * happening.
     */
    private static boolean wantsDeposit(RequirementResult.EntryResult entry, String depositId,
                                        ItemStack depositStack) {
        if ("item".equals(entry.getType())) return depositId.equals(entry.getId());
        if (!"item_tag".equals(entry.getType())) return false;

        String settled = entry.getSettledId();
        return (settled == null || settled.isEmpty())
                ? ItemTagResolution.matches(entry.getId(), depositStack)
                : settled.equals(depositId);
    }

    /** The icon for a card, or an empty stack when the kind has none. */
    private ItemStack requirementIcon(RequirementResult.EntryResult entry) {
        if ("item_tag".equals(entry.getType())) {
            // Unsettled, this walks the tag one member per second — so the card says "any of
            // these" without any text having to.
            return ItemTagResolution.displayStack(entry.getId(), entry.getSettledId(),
                    System.currentTimeMillis());
        }
        if ("item".equals(entry.getType())) {
            ResourceLocation rl = ResourceLocation.tryParse(entry.getId());
            if (rl != null) {
                var item = BuiltInRegistries.ITEM.get(rl);
                if (item != null) return new ItemStack(item);
            }
            return ItemStack.EMPTY;
        }
        if ("xp_level".equals(entry.getType())) {
            return new ItemStack(net.minecraft.world.item.Items.EXPERIENCE_BOTTLE);
        }
        return ItemStack.EMPTY;
    }

    /** Draws {@code text} cut to {@code maxWidth}, without a shadow — this is on parchment. */
    private void drawTrimmed(GuiGraphics guiGraphics, Component text, int x, int y, int maxWidth,
                             int colour) {
        guiGraphics.drawString(this.font, trimToWidth(text, maxWidth), x, y, colour, false);
    }

    /** The live booster multiplier, or null when no booster is under the pedestal. */
    /**
     * The research speed next to the title: the booster's multiplier combined with the stage's
     * cost blocks. Marked with a star when a cost block is part of it, so the player knows to
     * hover for the reason.
     */
    private Component speedLabel() {
        double multiplier = effectiveSpeedMultiplier();
        if (Math.abs(multiplier - 1.0) < 1e-6) return null;
        String text = Double.isInfinite(multiplier) ? "\u221E" : String.format("\u00D7%.2f", multiplier);
        return Component.literal(menu.getTimeFactorPercent() != 100 ? "\u2726" + text : text);
    }

    /** Booster speed times the cost blocks' time factor; infinite when a block makes it instant. */
    private double effectiveSpeedMultiplier() {
        double booster = BoosterUtil.speedMultiplier(menu.getCurrentSpeedPercent() / 100.0);
        int timePercent = menu.getTimeFactorPercent();
        if (timePercent <= 0) return Double.POSITIVE_INFINITY;
        return booster * 100.0 / timePercent;
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int pMouseX, int pMouseY) {
        // No block-name title and no "Inventory" caption: the artwork says both already, and
        // the band below the parchment is the only place a label fits — two lines, no more.

        ItemStack stack = menu.getSlot(36).getItem();

        if (!stack.isEmpty()) {
            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (tag.contains("StageResearch")) {
                String stageId = tag.getString("StageResearch");
                boolean isCreative = ModItems.CREATIVE_STAGE_ID.equals(stageId);
                boolean isIndividual = !isCreative && StageManager.isIndividualStage(stageId);

                String stageName;
                boolean alreadyUnlocked;

                if (isCreative) {
                    stageName = "Creative";
                    alreadyUnlocked = false;
                } else {
                    StageEntry entry;
                    if (isIndividual) {
                        entry = StageManager.getIndividualStages().get(stageId);
                        alreadyUnlocked = ClientIndividualStageCache.isStageUnlocked(stageId);
                    } else {
                        entry = StageManager.getStages().get(stageId);
                        alreadyUnlocked = ClientStageCache.isStageUnlocked(stageId);
                    }
                    stageName = entry != null ? entry.getDisplayName() : stageId;
                }

                int finishDelay = this.menu.data.get(2);

                // EXTERNAL/AUTO stages can be inserted but never researched here.
                // Show a clear message instead of progress UI.
                StageMode mode = null;
                if (!isCreative) {
                    StageEntry modeEntry = isIndividual
                            ? StageManager.getIndividualStages().get(stageId)
                            : StageManager.getStages().get(stageId);
                    if (modeEntry != null) mode = modeEntry.getMode();
                }
                boolean notResearchable = mode != null
                        && mode != StageMode.DEFAULT;

                if (alreadyUnlocked && finishDelay == 0) {
                    drawSplitLine(guiGraphics, PedestalLayout.LINE_1_Y,
                            Component.translatable("gui.historystages.pedestal.research", stageName),
                            SLAB_PRIMARY, speedLabel(), SLAB_SECONDARY);
                    drawCentred(guiGraphics, Component.translatable("screen.historystages.already_learned"),
                            PedestalLayout.LINE_2_Y, SLAB_ERROR);
                } else if (notResearchable) {
                    drawSplitLine(guiGraphics, PedestalLayout.LINE_1_Y,
                            Component.translatable("gui.historystages.pedestal.stage", stageName),
                            SLAB_PRIMARY, speedLabel(), SLAB_SECONDARY);
                    drawCentred(guiGraphics, Component.translatable("screen.historystages.not_researchable"),
                            PedestalLayout.LINE_2_Y, SLAB_ERROR);
                } else {
                    Component stageLine;
                    int nameColor;
                    if (finishDelay > 0) {
                        stageLine = Component.translatable("gui.historystages.pedestal.finalizing", stageName);
                        nameColor = SLAB_ACCENT;
                    } else {
                        stageLine = Component.translatable("gui.historystages.pedestal.researching", stageName);
                        nameColor = SLAB_PRIMARY;
                    }
                    // A research that would take something away says so on its title; the hover
                    // names what.
                    if (!BlockedLines.revokedByUnlocking(stageId, isIndividual).isEmpty()) {
                        stageLine = Component.literal("\u26A0 ").withStyle(net.minecraft.ChatFormatting.GOLD)
                                .append(stageLine.copy());
                    }
                    drawSplitLine(guiGraphics, PedestalLayout.LINE_1_Y,
                            stageLine, nameColor, speedLabel(), SLAB_SECONDARY);

                    // A warning owns the second line outright: the player has to act on it
                    // before a percentage means anything. Tier comes first — the scroll has to
                    // move before deposits matter.
                    if (menu.isTierMismatch()) {
                        String key = menu.getRequiredTierMode() == 1
                                ? "screen.historystages.tier_required.exact"
                                : "screen.historystages.tier_required.min";
                        drawCentred(guiGraphics,
                                Component.translatable(key, TierMatcher.roman(menu.getRequiredTier())),
                                PedestalLayout.LINE_2_Y, SLAB_ERROR);
                    } else if (menu.isLogicBlocked()) {
                        // Before "requirements not met": a blocked stage folds into that verdict
                        // on the server, and the player needs the actual reason, which the hover
                        // on this line spells out.
                        drawCentred(guiGraphics, trimToWidth(BlockedLines.header(), slabTextWidth()),
                                PedestalLayout.LINE_2_Y, SLAB_ERROR);
                    } else if (!menu.areDependenciesMet()) {
                        drawCentred(guiGraphics,
                                Component.translatable("screen.historystages.dependencies_not_met"),
                                PedestalLayout.LINE_2_Y, SLAB_ERROR);
                    } else if (tag.contains("ResearchProgress")) {
                        int currentProgress = tag.getInt("ResearchProgress");
                        int maxProgress = tag.contains("MaxProgress") ? tag.getInt("MaxProgress") : 400;

                        int percent = (int) (((double) currentProgress / maxProgress) * 100);
                        drawBarPercent(guiGraphics, Math.min(100, percent));

                        int remainingTicks = Math.max(0, maxProgress - currentProgress);
                        // Account for the live speed multiplier from the booster under the pedestal.
                        double speedMultiplier = effectiveSpeedMultiplier();
                        int effectiveRemainingTicks = Double.isInfinite(speedMultiplier) ? 0
                                : (int) Math.ceil(remainingTicks / speedMultiplier);
                        int remainingSeconds = (effectiveRemainingTicks / 20)
                                + (effectiveRemainingTicks % 20 > 0 ? 1 : 0);
                        if (percent >= 100) remainingSeconds = 0;

                        Component timeText;
                        if (remainingSeconds >= 60) {
                            int mins = remainingSeconds / 60;
                            int secs = remainingSeconds % 60;
                            timeText = Component.translatable(
                                    "gui.historystages.pedestal.remaining_time.minutes", mins, secs);
                        } else {
                            timeText = Component.translatable(
                                    "gui.historystages.pedestal.remaining_time.seconds", remainingSeconds);
                        }
                        drawLeft(guiGraphics, trimToWidth(timeText, slabTextWidth()),
                                PedestalLayout.LINE_2_Y, SLAB_SECONDARY);
                    }
                }

                // The owner is not repeated here — the scroll's own tooltip already names it,
                // and the band only has room for two lines.

            } else {
                drawLeft(guiGraphics, Component.translatable("gui.historystages.pedestal.invalid_book"),
                        PedestalLayout.LINE_1_Y, SLAB_ERROR);
            }
        } else {
            int ticks = (int) (Minecraft.getInstance().level.getGameTime() / 10) % 4;
            drawLeft(guiGraphics,
                    Component.translatable("gui.historystages.pedestal.searching").copy().append(".".repeat(ticks)),
                    PedestalLayout.LINE_1_Y, SLAB_SECONDARY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float pPartialTick, int pMouseX, int pMouseY) {
        // Update dependency state in case it changed (scroll inserted/removed)
        boolean prevDeps = hasDependencies;
        checkDependencies();

        // Re-initialize if state changed so imageWidth and leftPos are recalculated
        if (hasDependencies != prevDeps) {
            this.init(this.minecraft, this.width, this.height);
        }

        // One blit for the whole sheet, candles included: imageHeight covers everything that
        // is painted, so the screen centres on what the player actually sees.
        ResourceLocation sheet = tierTexture();
        guiGraphics.blit(sheet, this.leftPos, this.topPos,
                PedestalLayout.SHEET_X, PedestalLayout.SHEET_Y,
                PedestalLayout.WIDTH, PedestalLayout.HEIGHT, 256, 256);

        if (hasDependencies) {
            int depX = this.leftPos + PedestalLayout.WIDTH + PedestalLayout.DEP_GAP;
            // Hangs off the slab, not off the panel top — above the slab there are only candles.
            int depY = this.topPos + PedestalLayout.BODY_TOP;
            guiGraphics.blit(TEXTURE_DEP, depX, depY,
                    PedestalLayout.DEP_SHEET_X, PedestalLayout.DEP_SHEET_Y,
                    PedestalLayout.DEP_W, PedestalLayout.DEP_H, 256, 256);
            renderDependencyPanel(guiGraphics, depX, depY, pMouseX, pMouseY);
        }

        // Layer 2: a plain rectangle. The mask below gives it the bar's ragged shape.
        int fill = PedestalLayout.barFillWidth(this.menu.data.get(0), this.menu.data.get(1));
        if (fill > 0) {
            int fx = this.leftPos + PedestalLayout.BAR_X;
            int fy = this.topPos + PedestalLayout.BAR_Y;
            guiGraphics.fill(fx, fy, fx + fill, fy + PedestalLayout.BAR_H, PedestalLayout.BAR_COLOR);
        }
        // Layer 3: the mask — opaque everywhere the fill must not show, with the bar window
        // punched out of it.
        guiGraphics.blit(TEXTURE_BAR, this.leftPos, this.topPos,
                PedestalLayout.SHEET_X, PedestalLayout.SHEET_Y,
                PedestalLayout.WIDTH, PedestalLayout.HEIGHT, 256, 256);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        pendingTooltip = null;

        super.render(guiGraphics, mouseX, mouseY, delta);

        ItemStack stack = menu.getSlot(36).getItem();
        if (!stack.isEmpty()) {
            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (tag.contains("StageResearch")) {
                String stageId = tag.getString("StageResearch");

                // Cache invalidation: if deposited NBT changed, clear and force re-request
                CompoundTag currentDeposited = tag.getCompound("DepositedDependencies");
                if (lastDepositedNBT == null || !lastDepositedNBT.equals(currentDeposited)) {
                    ClientDependencyCache.remove(stageId, StageManager.isIndividualStage(stageId));
                    lastDepositedNBT = currentDeposited.copy();
                    lastDependencyCheck = 0; // force immediate re-request
                }

                // Grey the scroll while a research is running: that is exactly when it is
                // locked into the slot and cannot be taken out.
                if (menu.isRunning()) {
                    int slotX = this.leftPos + PedestalLayout.SCROLL_SLOT_X;
                    int slotY = this.topPos + PedestalLayout.SCROLL_SLOT_Y;
                    guiGraphics.pose().pushPose();
                    guiGraphics.pose().translate(0, 0, SLOT_OVERLAY_Z);
                    guiGraphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0x80808080);
                    guiGraphics.pose().popPose();
                }
            }
        }

        pendingTooltipLines = blockedTooltip(mouseX, mouseY);
        if (pendingTooltipLines == null) pendingTooltipLines = costTooltip(mouseX, mouseY);
        if (pendingTooltipLines == null) pendingTooltipLines = revokeTooltip(mouseX, mouseY);
        if (pendingTooltipLines != null) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 500);
            guiGraphics.renderComponentTooltip(this.font, pendingTooltipLines, mouseX, mouseY);
            guiGraphics.pose().popPose();
        } else if (pendingTooltip != null) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 500);
            guiGraphics.renderTooltip(this.font, pendingTooltip, mouseX, mouseY);
            guiGraphics.pose().popPose();
        } else {
            renderTooltip(guiGraphics, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseScrolled(double pMouseX, double pMouseY, double pScrollX, double pScrollY) {
        if (hasDependencies) {
            int panelX = this.leftPos + PedestalLayout.WIDTH + PedestalLayout.DEP_GAP;
            if (pMouseX >= panelX && pMouseX <= panelX + PedestalLayout.DEP_W) {
                int maxScroll = Math.max(0, totalContentHeight - PedestalLayout.DEP_LIST_H);
                if (maxScroll > 0) {
                    scrollAmount = (float) Math.max(0, Math.min(maxScroll, scrollAmount - pScrollY * 12));
                    return true;
                }
            }
        }
        return super.mouseScrolled(pMouseX, pMouseY, pScrollX, pScrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (hasDependencies && button == 0) {
            ItemStack scroll = menu.getSlot(36).getItem();
            if (!scroll.isEmpty()) {
                CompoundTag scrollTag = scroll.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                if (scrollTag.contains("StageResearch")) {
                    String stageId = scrollTag.getString("StageResearch");
                    RequirementResult result = ClientDependencyCache.get(stageId, StageManager.isIndividualStage(stageId));

                    if (result != null) {
                        int groupIdx = 0;
                        for (RequirementResult.GroupResult group : result.getGroups()) {
                            for (RequirementResult.EntryResult entry : group.getEntries()) {
                                if (entry.canDeposit()) {
                                    // XP deposit icon position
                                    int xpX = this.leftPos + PedestalLayout.WIDTH + PedestalLayout.DEP_GAP
                                            + PedestalLayout.DEP_XP_BUTTON_X;
                                    int xpY = this.topPos + PedestalLayout.DEP_XP_BUTTON_Y;
                                    if (mouseX >= xpX && mouseX < xpX + PedestalLayout.ICON_SIZE
                                            && mouseY >= xpY && mouseY < xpY + PedestalLayout.ICON_SIZE) {
                                        // The server would refuse this silently. The button is
                                        // already drawn pressed in and says why on hover, so the
                                        // click is swallowed rather than sent into nothing.
                                        if (canAfford(entry)) {
                                            PacketHandler.sendToServer(new DepositDependencyPacket(
                                                    menu.getBlockPos(), groupIdx, "XP", ""));
                                        }
                                        return true;
                                    }
                                }
                            }
                            groupIdx++;
                        }
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Whether the player can actually pay for a depositable requirement. {@code canDeposit} only
     * says the requirement takes a deposit at all — the pedestal's XP branch additionally refuses
     * anything the player cannot cover, and returns without a word when it does. A creative
     * player sits at level 0 with the XP bar hidden, which is exactly how a button that looks
     * live ends up doing nothing.
     */
    /**
     * The red "Blocked while" card at the top of the requirement list. Returns the y below it.
     * Lines are trimmed to the parchment; the status line's hover has the full text.
     */
    private int drawBlockedCard(GuiGraphics g, String stageId, boolean individual, int y) {
        List<BlockedLines.Line> reason = BlockedLines.reasonLines(
                BlockedLines.forLocalPlayer(stageId, individual), individual);
        int h = 13 + reason.size() * 10 + 2;
        g.fill(0, y, PedestalLayout.DEP_CONTENT_W, y + h, 0x40AA2222);
        g.fill(0, y, 2, y + h, 0xFFFF5555);
        drawTrimmed(g, BlockedLines.header(), CARD_PAD + 2, y + 3,
                PedestalLayout.DEP_CONTENT_W - CARD_PAD - 4, 0xFF5555);
        int lineY = y + 14;
        for (BlockedLines.Line line : reason) {
            int indent = CARD_PAD + 2 + line.indent() * 6;
            drawTrimmed(g, line.text(), indent, lineY, PedestalLayout.DEP_CONTENT_W - indent - 2, PANEL_PRIMARY);
            lineY += 10;
        }
        return y + h + CARD_GAP + 3;
    }

    private static boolean canAfford(RequirementResult.EntryResult entry) {
        return entry.getCurrent() >= entry.getRequired();
    }

    /**
     * The reason behind the "blocked" status line, when the mouse is on it. Null otherwise, and
     * null under PLAIN, where the header is all there is to say.
     */
    private List<Component> blockedTooltip(int mouseX, int mouseY) {
        if (!menu.isLogicBlocked() || !BlockedLines.showReason()) return null;
        int lineY = this.topPos + PedestalLayout.LINE_2_Y;
        if (mouseX < this.leftPos || mouseX > this.leftPos + PedestalLayout.WIDTH
                || mouseY < lineY - 1 || mouseY > lineY + 9) return null;
        String stageId = currentStageId();
        if (stageId == null) return null;
        var blocked = BlockedLines.forLocalPlayer(stageId, StageManager.isIndividualStage(stageId));
        List<Component> lines = new java.util.ArrayList<>();
        lines.add(BlockedLines.header().withStyle(net.minecraft.ChatFormatting.RED));
        for (BlockedLines.Line line : BlockedLines.reasonLines(blocked, StageManager.isIndividualStage(stageId))) {
            lines.add(Component.literal(line.indent() > 0 ? "   • " : " • ")
                    .append(line.text()).withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        return lines;
    }

    private static String costLabel(CompoundTag tag) {
        StringBuilder out = new StringBuilder();
        if (tag.contains("LockedCostReduction")) {
            long pct = Math.round(tag.getDouble("LockedCostReduction") * 100);
            if (pct >= 100) out.append("0");
            else if (pct != 0) out.append(pct > 0 ? "-" : "+").append(Math.abs(pct)).append('%');
        }
        if (tag.contains("LockedXpFactor")) {
            long pct = Math.round((tag.getDouble("LockedXpFactor") - 1.0) * 100);
            if (pct != 0) {
                if (out.length() > 0) out.append(' ');
                out.append("XP ").append(pct > 0 ? "+" : "-").append(Math.abs(pct)).append('%');
            }
        }
        return out.toString();
    }

    /**
     * Why this stage costs what it does, when the mouse is on the speed label: every cost block in
     * effect with its conditions. Null when none applies or the mouse is elsewhere.
     */
    private List<Component> costTooltip(int mouseX, int mouseY) {
        if (menu.getTimeFactorPercent() == 100 && !hasLogicCost()) return null;
        int lineY = this.topPos + PedestalLayout.LINE_1_Y;
        if (mouseX < this.leftPos + PedestalLayout.WIDTH / 2 || mouseX > this.leftPos + PedestalLayout.WIDTH
                || mouseY < lineY - 1 || mouseY > lineY + 9) return null;
        String stageId = currentStageId();
        if (stageId == null) return null;
        boolean individual = StageManager.isIndividualStage(stageId);
        var factors = BlockedLines.costForLocalPlayer(stageId, individual);
        if (factors.active().isEmpty()) return null;
        List<Component> lines = new java.util.ArrayList<>();
        for (var block : factors.active()) {
            lines.add(BlockedLines.costHeader(block, true).withStyle(net.minecraft.ChatFormatting.AQUA));
            for (BlockedLines.Line line : BlockedLines.conditionLines(block, individual)) {
                lines.add(Component.literal(line.indent() > 0 ? "   \u2022 " : " \u2022 ")
                        .append(line.text()).withStyle(net.minecraft.ChatFormatting.GRAY));
            }
        }
        return lines;
    }

    /** What researching this stage would take away, when the mouse is on the title. */
    private List<Component> revokeTooltip(int mouseX, int mouseY) {
        int lineY = this.topPos + PedestalLayout.LINE_1_Y;
        if (mouseX < this.leftPos || mouseX > this.leftPos + PedestalLayout.WIDTH / 2
                || mouseY < lineY - 1 || mouseY > lineY + 9) return null;
        String stageId = currentStageId();
        if (stageId == null) return null;
        List<String> lost = BlockedLines.revokedByUnlocking(stageId, StageManager.isIndividualStage(stageId));
        if (lost.isEmpty()) return null;
        List<Component> lines = new java.util.ArrayList<>();
        lines.add(BlockedLines.revokeTriggerHeader().withStyle(net.minecraft.ChatFormatting.GOLD));
        for (String name : lost) {
            lines.add(Component.literal(" \u2022 " + name).withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        return lines;
    }

    private boolean hasLogicCost() {
        String stageId = currentStageId();
        return stageId != null && !BlockedLines.costForLocalPlayer(stageId,
                StageManager.isIndividualStage(stageId)).active().isEmpty();
    }

    private String currentStageId() {
        ItemStack stack = menu.getSlot(36).getItem();
        if (stack.isEmpty()) return null;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains("StageResearch") ? tag.getString("StageResearch") : null;
    }

    private void renderDependencyPanel(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {
        ItemStack stack = menu.getSlot(36).getItem();
        if (stack.isEmpty()) return;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains("StageResearch")) return;
        String stageId = tag.getString("StageResearch");
        boolean individual = StageManager.isIndividualStage(stageId);

        RequirementResult result = ClientDependencyCache.get(stageId, individual);

        // Poll server once per second to keep dep status fresh
        long now = System.currentTimeMillis();
        if (now - lastDependencyCheck > 1000) {
            PacketHandler.sendToServer(new CheckDependencyPacket(stageId, individual, menu.getBlockPos()));
            lastDependencyCheck = now;
        }

        boolean isUnlocked = menu.isIndividualMode()
                ? ClientIndividualStageCache.isStageUnlocked(stageId)
                : ClientStageCache.isStageUnlocked(stageId);

        if (isUnlocked) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 100);
            Component text = Component.translatable("screen.historystages.already_learned");
            int tw = this.font.width(text);
            guiGraphics.drawString(this.font, text, x + (PedestalLayout.DEP_W / 2) - (tw / 2), y + 70,
                    PANEL_ERROR, false);
            guiGraphics.pose().popPose();
            return;
        }

        if (result == null) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 100);
            guiGraphics.drawString(this.font,
                    Component.translatable("gui.historystages.pedestal.loading"), x + 10, y + 20, PANEL_SECONDARY, false);
            guiGraphics.pose().popPose();
            return;
        }

        // Panel title
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 100);
        Component panelTitle = Component.translatable("gui.historystages.pedestal.requirements");
        guiGraphics.drawString(this.font, panelTitle,
                x + (PedestalLayout.DEP_W / 2) - (font.width(panelTitle) / 2),
                y + PedestalLayout.DEP_TITLE_Y, PANEL_PRIMARY, false);

        // No "Deposit:" caption — the slot and the flask button say what they are, and the
        // XP button explains itself through its tooltip.

        // Locked cost reduction from the scroll, beside the deposit controls
        // Frozen cost of this scroll: booster and cost blocks together. Costlier reads "+", and XP
        // shows only when a cost block changed it.
        String costLabel = costLabel(tag);
        if (!costLabel.isEmpty()) {
            guiGraphics.drawString(this.font, costLabel, x + 5, y + 154, PANEL_SECONDARY, false);
        }

        // Deposit progress bar (shows while an item is being processed)
        int dDelay = menu.data.get(5);
        if (dDelay > 0) {
            ItemStack depositStack = menu.getSlot(37).getItem();
            if (!depositStack.isEmpty()) {
                ResourceLocation dRl = BuiltInRegistries.ITEM.getKey(depositStack.getItem());
                String dId = dRl != null ? dRl.toString() : "";
                boolean isNeeded = result.getGroups().stream()
                        .flatMap(g -> g.getEntries().stream())
                        .anyMatch(e -> !e.isFulfilled() && wantsDeposit(e, dId, depositStack));
                if (isNeeded) {
                    int barWidth = 16;
                    int filledWidth = (int) ((double) dDelay / 20.0 * barWidth);
                    // Position: absolute screen coords for the deposit slot
                    int bx = this.leftPos + PedestalLayout.WIDTH + PedestalLayout.DEP_GAP
                            + PedestalLayout.DEP_SLOT_X;
                    int by = this.topPos + PedestalLayout.DEP_SLOT_Y + PedestalLayout.ICON_SIZE;
                    guiGraphics.fill(bx, by, bx + barWidth, by + 2, 0xFF404040);
                    guiGraphics.fill(bx, by, bx + filledWidth, by + 2, 0xFFAAAAAA);
                }
            }
        }
        guiGraphics.pose().popPose();

        // Clipped scrollable content area: the flat middle of the scroll, nothing wider.
        int clipX = x + PedestalLayout.DEP_CONTENT_X;
        int clipY = y + PedestalLayout.DEP_LIST_Y;
        int clipW = PedestalLayout.DEP_CONTENT_W;
        int clipH = PedestalLayout.DEP_LIST_H;

        guiGraphics.enableScissor(clipX, clipY, clipX + clipW, clipY + clipH);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(clipX, clipY - scrollAmount, 100);

        int currentY = 0;
        if (menu.isLogicBlocked()) {
            currentY = drawBlockedCard(guiGraphics, stageId, individual, currentY);
        }
        int groupIdx = 0;
        for (RequirementResult.GroupResult group : result.getGroups()) {
            if (result.getGroups().size() > 1) {
                guiGraphics.drawString(this.font,
                        Component.translatable("gui.historystages.pedestal.group", groupIdx + 1),
                        CARD_PAD, currentY, 0x606060, false);
                currentY += 12;
            }

            for (RequirementResult.EntryResult entry : group.getEntries()) {
                boolean fulfilled = entry.isFulfilled();

                // One card shape for every requirement: a kind on top, the value under it.
                // The kind matters — "Bronze Age" alone does not say whether it is a stage,
                // an advancement or a scoreboard objective.
                guiGraphics.fill(0, currentY, PedestalLayout.DEP_CONTENT_W, currentY + CARD_H,
                        fulfilled ? 0x202E8B57 : 0x20AA3333);

                ItemStack icon = requirementIcon(entry);
                int textX = CARD_PAD;
                if (!icon.isEmpty()) {
                    guiGraphics.renderItem(icon, 1, currentY + (CARD_H - 16) / 2);
                    textX = 18 + CARD_PAD;
                }
                int textW = PedestalLayout.DEP_CONTENT_W - CARD_PAD - textX;

                drawTrimmed(guiGraphics, requirementKind(entry), textX, currentY + 3, textW,
                        PANEL_SECONDARY);
                drawTrimmed(guiGraphics, requirementValue(entry), textX, currentY + 13, textW,
                        fulfilled ? PANEL_ACCENT : PANEL_PRIMARY);

                // The parchment is only 82px wide, so most values are cut. The full text
                // always stays reachable on hover.
                if (mouseX >= clipX && mouseX <= clipX + clipW
                        && mouseY >= clipY + currentY - scrollAmount
                        && mouseY < clipY + currentY + CARD_H - scrollAmount
                        && mouseY >= clipY && mouseY <= clipY + clipH) {
                    pendingTooltip = requirementTooltip(entry);
                }
                currentY += CARD_H + CARD_GAP;
            }
            groupIdx++;
            currentY += 5;
        }
        totalContentHeight = currentY;

        guiGraphics.pose().popPose();
        guiGraphics.disableScissor();

        // Suppress tooltip when hovering directly over the deposit slot
        int slotX = this.leftPos + PedestalLayout.WIDTH + PedestalLayout.DEP_GAP
                + PedestalLayout.DEP_SLOT_X;
        int slotY = this.topPos + PedestalLayout.DEP_SLOT_Y;
        if (mouseX >= slotX - 1 && mouseX <= slotX + PedestalLayout.ICON_SIZE - 1
                && mouseY >= slotY - 1 && mouseY <= slotY + PedestalLayout.ICON_SIZE - 1) {
            pendingTooltip = null;
        }

        // Scrollbar, inside the parchment's right edge — beyond it is the rolled border.
        if (totalContentHeight > clipH) {
            int barX = clipX + clipW - 2;
            int barY = clipY + 2;
            int barH = clipH - 4;
            guiGraphics.fill(barX, barY, barX + 2, barY + barH, 0x40000000);

            float scrollPercent = scrollAmount / (float) (totalContentHeight - clipH);
            int thumbH = Math.max(10, (int) (barH * ((float) clipH / totalContentHeight)));
            int thumbY = (int) (scrollPercent * (barH - thumbH));
            guiGraphics.fill(barX, barY + thumbY, barX + 2, barY + thumbY + thumbH, 0x80FFFFFF);
        }

        // XP deposit icon (shown when an XP deposit is needed and not yet fulfilled)
        for (RequirementResult.GroupResult group : result.getGroups()) {
            for (RequirementResult.EntryResult entry : group.getEntries()) {
                if (entry.canDeposit()) {
                    int xpX = this.leftPos + PedestalLayout.WIDTH + PedestalLayout.DEP_GAP
                            + PedestalLayout.DEP_XP_BUTTON_X;
                    int xpY = this.topPos + PedestalLayout.DEP_XP_BUTTON_Y;
                    boolean xpHovered = mouseX >= xpX && mouseX < xpX + PedestalLayout.ICON_SIZE
                            && mouseY >= xpY && mouseY < xpY + PedestalLayout.ICON_SIZE;
                    // The server drops a deposit the player cannot pay for without a word, so
                    // the button has to say so itself. For an unpaid consume-XP requirement
                    // `current` is the player's own level and `required` the price, both kept
                    // fresh by the once-a-second poll above.
                    boolean affordable = canAfford(entry);
                    // Same convention as PedestalIconButton: a button that would do nothing
                    // sits pressed in, which is also the hover face.
                    guiGraphics.blit(TEXTURE_BUTTONS, xpX, xpY,
                            PedestalLayout.ICON_COL_XP * PedestalLayout.ICON_SIZE,
                            (xpHovered || !affordable) ? PedestalLayout.ICON_SIZE : 0,
                            PedestalLayout.ICON_SIZE, PedestalLayout.ICON_SIZE, 54, 36);
                    // Replaces the old "Deposit:" caption: the button says what it does only
                    // when the player asks it to.
                    if (xpHovered) {
                        pendingTooltip = affordable
                                ? Component.translatable("gui.historystages.pedestal.pay_xp")
                                : Component.translatable("gui.historystages.pedestal.pay_xp_missing",
                                        entry.getRequired(), entry.getCurrent());
                    }
                    return; // Only show one button
                }
            }
        }
    }
}
