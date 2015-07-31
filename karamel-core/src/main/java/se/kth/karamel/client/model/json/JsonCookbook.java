/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package se.kth.karamel.client.model.json;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import se.kth.karamel.client.model.Cookbook;

/**
 *
 * @author kamal
 */
public class JsonCookbook extends Cookbook {

  String name;
  //values of attrs could be string or array of string 
  Map<String, Object> attrs = new HashMap<>();
  Set<JsonRecipe> recipes = new HashSet<>();
  
  public JsonCookbook() {
  }

  public JsonCookbook(Cookbook cb, String name, Map<String, Object> attrs) {
    super(cb);
    this.name = name;
    this.attrs = attrs;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Map<String, Object> getAttrs() {
    return attrs;
  }

  public void setAttrs(Map<String, Object> attrs) {
    this.attrs = attrs;
  }

  public Set<JsonRecipe> getRecipes() {
    return recipes;
  }

  public void setRecipes(Set<JsonRecipe> recipes) {
    this.recipes = recipes;
  }
  
}