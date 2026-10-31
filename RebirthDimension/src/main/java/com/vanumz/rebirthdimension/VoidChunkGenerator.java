package com.vanumz.rebirthdimension;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;

import java.util.Random;

/** Generator kosong: tidak ada blok sama sekali (tanpa platform bedrock). */
public final class VoidChunkGenerator extends ChunkGenerator {

    private final double x;
    private final double y;
    private final double z;

    public VoidChunkGenerator(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, x, y, z);
    }
}
