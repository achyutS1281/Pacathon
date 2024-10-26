package com.buaisociety.pacman.entity.behavior;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.buaisociety.pacman.Searcher;
import com.buaisociety.pacman.maze.Maze;
import com.buaisociety.pacman.maze.Tile;
import com.buaisociety.pacman.maze.TileState;
import com.buaisociety.pacman.sprite.DebugDrawing;
import com.cjcrafter.neat.Client;
import com.buaisociety.pacman.entity.Direction;
import com.buaisociety.pacman.entity.Entity;
import com.buaisociety.pacman.entity.PacmanEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2i;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class NeatPacmanBehavior implements Behavior {

    private final @NotNull Client client;
    private @Nullable PacmanEntity pacman;

    // Score modifiers help us maintain "multiple pools" of points.
    // This is great for training, because we can take away points from
    // specific pools of points instead of subtracting from all.
    private double scoreModifier = 0;
    private int updatessincelastScored = 0;
    private int lastScore = 0;
    private int movesSinceSLastScored = 0;
    private Direction lastDirection;
    private ArrayList<Float> lastPelletDists = new ArrayList<>();
    private Map<Vector2i, Integer> tilesVisited = new HashMap<>();
    public NeatPacmanBehavior(@NotNull Client client) {
        this.client = client;
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
        if (pacman == null) {
            pacman = (PacmanEntity) entity;
        }
        int newScore = pacman.getMaze().getLevelManager().getScore();
        Direction newMoveDirection = pacman.getDirection();

        if (newScore > lastScore) {
            updatessincelastScored = 0;
            lastScore = newScore;
            for (Map.Entry<Vector2i, Integer> tiles : tilesVisited.entrySet()) {
                tiles.setValue(0);
            }
        } else {
            updatessincelastScored++;
            if (updatessincelastScored >= 60 * 25) {
                pacman.kill();
                return Direction.UP;
            }
        }

        // Pellet Pathfinding
        //Find nearest Pellet



        // SPECIAL TRAINING CONDITIONS
        // TODO: Make changes here to help with your training...
        // END OF SPECIAL TRAINING CONDITIONS

        // We are going to use these directions a lot for different inputs. Get them all once for clarity and brevity
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
            nearestPelletForward *= 100.0f; // Ensure minimum weight
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
        float[] outputs = client.getCalculator().calculate(new float[]{
            canMoveForward ? 1f : 0f,
            canMoveLeft ? 1f : 0f,
            canMoveRight ? 1f : 0f,
            canMoveBehind ? 1f : 0f,
            nearestPelletForward,
            nearestPelletLeft,
            nearestPelletRight,
            nearestPelletBehind,
        }).join();
        int index = 0;
        float max = outputs[0];
        for (int i = 1; i < outputs.length; i++) {
            if (outputs[i] > max) {
                max = outputs[i];
                index = i;
            }
        }

        Direction newDirection = switch (index) {
            case 0 -> pacman.getDirection();
            case 1 -> pacman.getDirection().left();
            case 2 -> pacman.getDirection().right();
            case 3 -> pacman.getDirection().behind();
            default -> throw new IllegalStateException("Unexpected value: " + index);
        };
        if((newDirection == left && nearestPelletMax == nearestPelletLeft) || (newDirection == right && nearestPelletMax == nearestPelletRight) || (newDirection == behind && nearestPelletMax == nearestPelletBehind)) {
            scoreModifier += 1;
        }else{
            scoreModifier = Math.max(0, scoreModifier-1);
        }
        if(!pacman.canMove(newDirection)) {
            scoreModifier -= 0.1;
        }
        /*if (pacman.getMaze().getTile(pacman.getTilePosition().add(newDirection.asVector())).getState() != TileState.PELLET && (pelletBehind || pelletForward || pelletLeft || pelletRight)) {
            pacman.kill();
            return Direction.UP;
        }*/
        client.setScore(pacman.getMaze().getLevelManager().getScore() + scoreModifier);
        return newDirection;
    }

    @Override
    public void render(@NotNull SpriteBatch batch) {
        // TODO: You can render debug information here

        if (pacman != null) {
            DebugDrawing.outlineTile(batch, pacman.getMaze().getTile(pacman.getTilePosition()), Color.RED);
            DebugDrawing.drawDirection(batch, pacman.getTilePosition().x() * Maze.TILE_SIZE, pacman.getTilePosition().y() * Maze.TILE_SIZE, pacman.getDirection(), Color.RED);
        }

    }
}
