package com.groupbyinc.api.model;

import com.groupbyinc.common.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * <code>
 * A biasing profile defined at query time.
 + </code>
 * Created by groupby on 1/12/16.
 */
public class Biasing {

  @JsonInclude(JsonInclude.Include.NON_EMPTY) private List<String> bringToTop = new ArrayList<String>();

  @JsonInclude(JsonInclude.Include.NON_DEFAULT) private boolean augmentBiases = false;

  private Float influence = null;

  @JsonInclude(JsonInclude.Include.NON_EMPTY) private List<Bias> biases = new ArrayList<Bias>();

  /**
   * @return The list of product IDs
   */
  public List<String> getBringToTop() {
    return bringToTop;
  }

  /**
   * <code>
   * A list of product IDs to bring to the top of the result set. This list
   * will ensure that the products are included in the result set and appear in the order
   * defined.
   * </code>
   *
   * @param bringToTop
   *      The list of productIds.
   * @return
   */
  public Biasing setBringToTop(List<String> bringToTop) {
    this.bringToTop = bringToTop;
    return this;
  }

  /**
   * @return True if this biasing profile should augment the biases defined
   * in Command Center, false otherwise.
   */
  public boolean isAugmentBiases() {
    return augmentBiases;
  }

  /**
   * <code>
   * Set whether the biases defined should override or augment the biases
   * defined in Command Center
   *
   * </code>
   *
   * @param augmentBiases
   *         True to augment biases, false otherwise
   *
   * @return
   */
  public Biasing setAugmentBiases(boolean augmentBiases) {
    this.augmentBiases = augmentBiases;
    return this;
  }

  /**
   * @return The list of biases
   */
  public List<Bias> getBiases() {
    return biases;
  }

  /**
   * <code>
   * Set the list of biases
   * </code>
   *
   * @param biases
   *         The biases
   *
   * @return
   */
  public Biasing setBiases(List<Bias> biases) {
    this.biases = biases;
    return this;
  }

  /**
   * @return The influence of the biases.
   */
  public Float getInfluence() {
    return influence;
  }

  /**
   * <code>
   * Set the influence of the biases.
   * </code>
   *
   * @param influence
   *          The influence of the biases.
   */
  public Biasing setInfluence(Float influence) {
    this.influence = influence;
    return this;
  }
}
