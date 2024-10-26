package com.buaisociety.pacman.entity.behavior;

import com.buaisociety.pacman.Searcher;
import com.buaisociety.pacman.entity.Direction;
import com.buaisociety.pacman.entity.Entity;
import com.buaisociety.pacman.entity.PacmanEntity;
import com.buaisociety.pacman.maze.Tile;
import com.buaisociety.pacman.maze.TileState;
import com.cjcrafter.neat.compute.Calculator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2i;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class TournamentBehavior implements Behavior {

    private final Calculator calculator;
    private @Nullable PacmanEntity pacman;

    private int previousScore = 0;
    private int framesSinceScoreUpdate = 0;
    private double scoreModifier = 0;
    private int updatessincelastScored = 0;
    private int lastScore = 0;
    private int movesSinceSLastScored = 0;
    private Direction lastDirection;
    private ArrayList<Float> lastPelletDists = new ArrayList<>();
    private Map<Vector2i, Integer> tilesVisited = new HashMap<>();
    public TournamentBehavior(Calculator calculator) {
        this.calculator = calculator;
    }

    /**
     * Returns the desired direction that the entity should move towards.
     *
     * @param entity the entity to get the direction for
     * @return the desired direction for the entity
     */
    @NotNull
    @Override
    public Direction getDirection(@NotNull Entity entity) {
        // --- DO NOT REMOVE ---
        if (pacman == null) {
            pacman = (PacmanEntity) entity;
        }

        int newScore = pacman.getMaze().getLevelManager().getScore();
        if (previousScore != newScore) {
            previousScore = newScore;
            framesSinceScoreUpdate = 0;
        } else {
            framesSinceScoreUpdate++;
        }

        if (framesSinceScoreUpdate > 60 * 40) {
            pacman.kill();
            framesSinceScoreUpdate = 0;
        }
        // --- END OF DO NOT REMOVE ---

        // TODO: Put all your code for info into the neural network here

        Direction forward = pacman.getDirection();
        Direction left = pacman.getDirection().left();
        Direction right = pacman.getDirection().right();
        Direction behind = pacman.getDirection().behind();
        boolean canMoveForward = pacman.canMove(forward);
        boolean canMoveLeft = pacman.canMove(left);
        boolean canMoveRight = pacman.canMove(right);
        boolean canMoveBehind = pacman.canMove(behind);
        Tile currentTile = pacman.getMaze().getTile(pacman.getTilePosition());
        Map<Direction, Searcher.SearchResult> nearestPellets = Searcher.findTileInAllDirections(currentTile, tile -> tile.getState() == TileState.PELLET);

        int maxDistance = -1;
        for (Searcher.SearchResult result : nearestPellets.values()) {
            if (result != null) {
                maxDistance = Math.max(maxDistance, result.getDistance());
            }
        }

        float decayFactor = 0.5f;  // Adjust for desired sensitivity
        float closeThreshold = 3.0f; // Distance threshold to differentiate close vs. far pellets
        float minWeight = 0.1f; // Minimum weight for distant pellets

        // Step 1: Calculate weights for each direction
        float nearestPelletForward = nearestPellets.get(forward) != null ?
                                     (float) Math.exp(-decayFactor * (nearestPellets.get(forward).getDistance() - 1)) : 0.0f;
        float nearestPelletLeft = nearestPellets.get(left) != null ?
                                  (float) Math.exp(-decayFactor * (nearestPellets.get(left).getDistance() - 1)) : 0.0f;
        float nearestPelletRight = nearestPellets.get(right) != null ?
                                   (float) Math.exp(-decayFactor * (nearestPellets.get(right).getDistance() - 1)) : 0.0f;
        float nearestPelletBehind = nearestPellets.get(behind) != null ?
                                    (float) Math.exp(-decayFactor * (nearestPellets.get(behind).getDistance() - 1)) : 0.0f;

        // Step 2: Introduce weighting based on distance
        // Close pellets receive a boost
        if (nearestPellets.get(forward) != null && nearestPellets.get(forward).getDistance() < closeThreshold) {
            nearestPelletForward *= 1.5f; // Boost for close pellets
        } else if (nearestPelletForward < minWeight) {
            nearestPelletForward *= 10.0f; // Ensure minimum weight
        }

        if (nearestPellets.get(left) != null && nearestPellets.get(left).getDistance() < closeThreshold) {
            nearestPelletLeft *= 1.5f;
        } else if (nearestPelletLeft < minWeight) {
            nearestPelletLeft *= 10.0f; // Ensure minimum weight
        }

        if (nearestPellets.get(right) != null && nearestPellets.get(right).getDistance() < closeThreshold) {
            nearestPelletRight *= 1.5f;
        } else if (nearestPelletRight < minWeight) {
            nearestPelletRight *= 10.0f; // Ensure minimum weight
        }

        if (nearestPellets.get(behind) != null && nearestPellets.get(behind).getDistance() < closeThreshold) {
            nearestPelletBehind *= 1.5f;
        } else if (nearestPelletBehind < minWeight) {
            nearestPelletBehind *= 10.0f; // Ensure minimum weight
        }

        // Step 3: Normalize the weights
        float maxWeight = Math.max(nearestPelletForward, Math.max(nearestPelletLeft, Math.max(nearestPelletRight, nearestPelletBehind)));

        if (maxWeight > 0) {
            nearestPelletForward /= maxWeight;
            nearestPelletLeft /= maxWeight;
            nearestPelletRight /= maxWeight;
            nearestPelletBehind /= maxWeight;
        } else {
            // If all weights are below minimum, assign them equally
            nearestPelletForward = nearestPelletLeft = nearestPelletRight = nearestPelletBehind = 0.25f;
        }
        float nearestPelletMax = Math.max(nearestPelletForward, Math.max(nearestPelletLeft, Math.max(nearestPelletRight, nearestPelletBehind)));
        tilesVisited.put(pacman.getTilePosition(), tilesVisited.getOrDefault(pacman.getTilePosition(), 0) + 1);
        if (tilesVisited.get(pacman.getTilePosition()) >= 140) {
            scoreModifier -= 0.1;
        }
        float tilesVisitedForward = tilesVisited.getOrDefault(pacman.getTilePosition().add(forward.asVector()), 0);
        float tilesVisitedBehind = tilesVisited.getOrDefault(pacman.getTilePosition().add(behind.asVector()), 0);
        float tilesVisitedRight = tilesVisited.getOrDefault(pacman.getTilePosition().add(right.asVector()), 0);
        float tilesVisitedLeft = tilesVisited.getOrDefault(pacman.getTilePosition().add(left.asVector()), 0);
        float maxTilesVisited = Math.max(tilesVisitedForward, Math.max(tilesVisitedBehind, Math.max(tilesVisitedRight, tilesVisitedLeft)));
        if (maxTilesVisited > 0) {
            tilesVisitedForward /= maxTilesVisited;
            tilesVisitedBehind /= maxTilesVisited;
            tilesVisitedRight /= maxTilesVisited;
            tilesVisitedLeft /= maxTilesVisited;
        }
        float[] outputs = calculator.calculate(new float[]{
            canMoveForward ? 1f : 0f,
            canMoveLeft ? 1f : 0f,
            canMoveRight ? 1f : 0f,
            canMoveBehind ? 1f : 0f,
            nearestPelletForward,
            nearestPelletLeft,
            nearestPelletRight,
            nearestPelletBehind,
        }).join();

        // Chooses the maximum output as the direction to go... feel free to change this ofc!
        // Adjust this to whatever you used in the NeatPacmanBehavior.class
        int index = 0;
        float max = outputs[0];
        for (int i = 1; i < outputs.length; i++) {
            if (outputs[i] > max) {
                max = outputs[i];
                index = i;
            }
        }

        return switch (index) {
            case 0 -> pacman.getDirection();
            case 1 -> pacman.getDirection().left();
            case 2 -> pacman.getDirection().right();
            case 3 -> pacman.getDirection().behind();
            default -> throw new IllegalStateException("Unexpected value: " + index);
        };
    }
}
