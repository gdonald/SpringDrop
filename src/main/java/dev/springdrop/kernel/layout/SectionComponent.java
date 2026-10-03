package dev.springdrop.kernel.layout;

import dev.springdrop.kernel.block.BlockInstance;

/** One block placed in a section: the region it sits in, its weight there, and the block itself. */
public record SectionComponent(String region, int weight, BlockInstance block) {

    public SectionComponent inRegion(String newRegion) {
        return new SectionComponent(newRegion, weight, block);
    }

    public SectionComponent withWeight(int newWeight) {
        return new SectionComponent(region, newWeight, block);
    }

    public SectionComponent withBlock(BlockInstance newBlock) {
        return new SectionComponent(region, weight, newBlock);
    }
}
