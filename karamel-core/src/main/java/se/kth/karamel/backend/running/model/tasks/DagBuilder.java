/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package se.kth.karamel.backend.running.model.tasks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import org.apache.log4j.Logger;
import se.kth.karamel.backend.converter.ChefJsonGenerator;
import se.kth.karamel.backend.converter.UserClusterDataExtractor;
import se.kth.karamel.backend.dag.Dag;
import se.kth.karamel.backend.running.model.NodeRunTime;
import se.kth.karamel.common.launcher.amazon.InstanceType;
import se.kth.karamel.backend.machines.TaskSubmitter;
import se.kth.karamel.backend.running.model.ClusterRuntime;
import se.kth.karamel.backend.running.model.GroupRuntime;
import se.kth.karamel.common.stats.ClusterStats;
import se.kth.karamel.client.api.CookbookCache;
import se.kth.karamel.common.clusterdef.Ec2;
import se.kth.karamel.common.clusterdef.Provider;
import se.kth.karamel.common.clusterdef.json.JsonCluster;
import se.kth.karamel.common.clusterdef.json.JsonCookbook;
import se.kth.karamel.common.clusterdef.json.JsonGroup;
import se.kth.karamel.common.clusterdef.json.JsonRecipe;
import se.kth.karamel.common.util.Settings;
import se.kth.karamel.common.exception.DagConstructionException;
import se.kth.karamel.common.exception.KaramelException;
import se.kth.karamel.common.cookbookmeta.CookbookUrls;
import se.kth.karamel.common.cookbookmeta.KaramelizedCookbook;
import se.kth.karamel.common.cookbookmeta.KaramelFileYamlDeps;
import se.kth.karamel.common.util.Confs;

/**
 *
 * @author kamal
 */
public class DagBuilder {

  private static final Logger logger = Logger.getLogger(DagBuilder.class);

  public static Dag getInstallationDag(JsonCluster cluster, ClusterRuntime clusterEntity, ClusterStats clusterStats,
      TaskSubmitter submitter, Map<String, JsonObject> chefJsons) throws KaramelException {
    Dag dag = new Dag();
    Map<String, RunRecipeTask> allRecipeTasks = new HashMap<>();
    machineLevelTasks(cluster, clusterEntity, clusterStats, submitter, dag);
    //TODO temporary return from here without adding installations
    if (cluster.getUseContainers()){
      return dag;
    }
    cookbookLevelTasks(cluster, clusterEntity, clusterStats, chefJsons, submitter, allRecipeTasks, dag);
    Map<String, Map<String, Task>> rlts = recipeLevelTasks(cluster, clusterEntity, clusterStats, chefJsons, submitter,
        allRecipeTasks, dag);
    updateKaramelDependencies(allRecipeTasks, dag, rlts);
    return dag;
  }

  public static Dag getContainerSetupDag(ClusterRuntime clusterEntity, ClusterStats clusterStats,
                                         TaskSubmitter submitter,String keyValueStoreIp) throws KaramelException {
    Dag dag = new Dag();

    for (GroupRuntime ge : clusterEntity.getGroups()) {
      for (NodeRunTime me : ge.getMachines()) {
        FindOsTypeTask findOs = new FindOsTypeTask(me, clusterStats, submitter);
        Task dockerInstallationTask = new DockerInstallTask(me, clusterStats, submitter,keyValueStoreIp);
        try {
          dag.addTask(dockerInstallationTask);
          dag.addTask(findOs);
          if(keyValueStoreIp.equals(me.getPrivateIp())){
            Task dockerNetworkInstallationTask = new DockerNetworkSetupTask(me,clusterStats,submitter,keyValueStoreIp);
            dag.addTask(dockerNetworkInstallationTask);
          }
        } catch (DagConstructionException e) {
          throw new KaramelException("Error while configuring docker hosts", e);
        }
      }
    }
    return dag;

  }

  private static boolean updateKaramelDependencies(Map<String, RunRecipeTask> allRecipeTasks, Dag dag,
      Map<String, Map<String, Task>> rlts) throws KaramelException {
    boolean newDepFound = false;
    for (RunRecipeTask task : allRecipeTasks.values()) {
      String tid = task.uniqueId();
      KaramelizedCookbook kcb = CookbookCache.get(task.getCookbookId());
      KaramelFileYamlDeps dependency = kcb.getKaramelFile().getDependency(task.getRecipeCanonicalName());
      if (dependency != null) {
        for (String depRec : dependency.getLocal()) {
          String depId = RunRecipeTask.makeUniqueId(task.getMachineId(), depRec);
          newDepFound |= dag.addDependency(depId, tid);
        }

        for (String depRec : dependency.getGlobal()) {
          Map<String, Task> rlt2 = rlts.get(depRec);
          if (rlt2 != null) {
            for (Map.Entry<String, Task> entry : rlt2.entrySet()) {
              Task t7 = entry.getValue();
              newDepFound |= dag.addDependency(t7.uniqueId(), tid);
            }
          }
        }
      }
    }
    return newDepFound;
  }

  /**
   * Creates all recipe tasks for cluster and groups them by recipe-name. In other words, by having a recipe-name such
   * as hadoop::dn you fetch all the tasks in the cluster that are running hadoop::dn. recipeName -> taskid(recipe +
   * machineid) -> task
   *
   * @param cluster
   * @param clusterEntity
   * @param clusterStats
   * @param chefJsons
   * @param submitter
   * @param allRecipeTasks
   * @param dag
   * @return
   * @throws KaramelException
   */
  public static Map<String, Map<String, Task>> recipeLevelTasks(JsonCluster cluster, ClusterRuntime clusterEntity,
      ClusterStats clusterStats, Map<String, JsonObject> chefJsons, TaskSubmitter submitter,
      Map<String, RunRecipeTask> allRecipeTasks, Dag dag) throws KaramelException {
    Map<String, Map<String, Task>> map = new HashMap<>();
    for (GroupRuntime ge : clusterEntity.getGroups()) {
      JsonGroup jg = UserClusterDataExtractor.findGroup(cluster, ge.getName());
      for (NodeRunTime me : ge.getMachines()) {
        for (JsonCookbook jc : jg.getCookbooks()) {
          CookbookUrls urls = jc.getKaramelizedCookbook().getUrls();
          for (JsonRecipe rec : jc.getRecipes()) {
            JsonObject json1 = chefJsons.get(me.getId() + rec.getCanonicalName());
            addRecipeTaskForMachineIntoRecipesMap(rec.getCanonicalName(), me, clusterStats, map, json1, submitter,
                urls.id, jc.getName(), allRecipeTasks, dag);
          }
        }
      }
    }
    return map;
  }

  /*
   * Makes sure recipe-task for machine exists both in the DAG and in the grouping map of recipes
   */
  private static RunRecipeTask addRecipeTaskForMachineIntoRecipesMap(String recipeName, NodeRunTime machine,
      ClusterStats clusterStats, Map<String, Map<String, Task>> map, JsonObject chefJson, TaskSubmitter submitter,
      String cookbookId, String cookbookName, Map<String, RunRecipeTask> allRecipeTasks, Dag dag)
      throws DagConstructionException {
    RunRecipeTask t1 = makeRecipeTaskIfNotExist(recipeName, machine, clusterStats, chefJson, submitter, cookbookId,
        cookbookName, allRecipeTasks, dag);
    Map<String, Task> map1 = map.get(recipeName);
    if (map1 == null) {
      map1 = new HashMap<>();
      map.put(recipeName, map1);
    }
    map1.put(t1.uniqueId(), t1);
    return t1;
  }

  /*
   * Finds recipe task for machine if it has been already created otherwise makes a new one and adds it into the DAG
   */
  private static RunRecipeTask makeRecipeTaskIfNotExist(String recipeName, NodeRunTime machine,
      ClusterStats clusterStats, JsonObject chefJson,
      TaskSubmitter submitter, String cookbookId, String cookbookName, Map<String, RunRecipeTask> allRecipeTasks,
      Dag dag) throws DagConstructionException {
    String recId = RunRecipeTask.makeUniqueId(machine.getId(), recipeName);
    RunRecipeTask runRecipeTask = allRecipeTasks.get(recId);
    if (!allRecipeTasks.containsKey(recId)) {
      ChefJsonGenerator.addRunListForRecipe(chefJson, recipeName);
      GsonBuilder builder = new GsonBuilder();
      builder.disableHtmlEscaping();
      Gson gson = builder.setPrettyPrinting().create();
      String jsonString = gson.toJson(chefJson);
      runRecipeTask
          = new RunRecipeTask(machine, clusterStats, recipeName, jsonString, submitter, cookbookId, cookbookName);
      dag.addTask(runRecipeTask);
    }
    allRecipeTasks.put(recId, runRecipeTask);
    return runRecipeTask;
  }

  /**
   * machine -> taskid -> task
   *
   * @param cluster
   * @param clusterEntity
   * @param clusterStats
   * @param chefJsons
   * @param submitter
   * @param allRecipeTasks
   * @param dag
   * @return
   * @throws KaramelException
   */
  public static Map<String, Map<String, Task>> cookbookLevelTasks(JsonCluster cluster, ClusterRuntime clusterEntity,
      ClusterStats clusterStats, Map<String, JsonObject> chefJsons, TaskSubmitter submitter,
      Map<String, RunRecipeTask> allRecipeTasks, Dag dag) throws KaramelException {
    Map<String, Map<String, Task>> map = new HashMap<>();
    for (GroupRuntime ge : clusterEntity.getGroups()) {
      JsonGroup jg = UserClusterDataExtractor.findGroup(cluster, ge.getName());
      for (NodeRunTime me : ge.getMachines()) {
        Map<String, Task> map1 = new HashMap<>();
        for (JsonCookbook jc : jg.getCookbooks()) {
          CookbookUrls urls = jc.getKaramelizedCookbook().getUrls();
          VendorCookbookTask t1 = new VendorCookbookTask(me, clusterStats, submitter, urls.id,
              Settings.REMOTE_CB_VENDOR_PATH,
              urls.repoUrl, urls.repoName, urls.cookbookRelPath, urls.branch);
          dag.addTask(t1);
          map1.put(t1.uniqueId(), t1);
          String recipeName = jc.getName() + Settings.COOKBOOK_DELIMITER + Settings.INSTALL_RECIPE;
          JsonObject json = chefJsons.get(me.getId() + recipeName);
          RunRecipeTask t2 = makeRecipeTaskIfNotExist(recipeName, me, clusterStats,
              json, submitter, urls.id, jc.getName(),
              allRecipeTasks, dag);
          map1.put(t2.uniqueId(), t2);
        }
        logger.debug(String.format("Cookbook-level tasks for the machine '%s' in the group '%s' are: %s",
            me.getPublicIp(), ge.getName(), map1.keySet()));
        if (map.get(me.getId()) != null) {
          map.get(me.getId()).putAll(map1);
        } else {
          map.put(me.getId(), map1);
        }
      }
    }
    return map;
  }

  public static void machineLevelTasks(JsonCluster cluster, ClusterRuntime clusterEntity, ClusterStats clusterStats,
      TaskSubmitter submitter, Dag dag) throws KaramelException {
    Confs confs = Confs.loadKaramelConfs();
    String prepStoragesConf = confs.getProperty(Settings.PREPARE_STORAGES_KEY);
    String vendorPath = UserClusterDataExtractor.makeVendorPath(cluster);
    for (GroupRuntime ge : clusterEntity.getGroups()) {
      for (NodeRunTime me : ge.getMachines()) {
        FindOsTypeTask findOs = new FindOsTypeTask(me, clusterStats, submitter);
        dag.addTask(findOs);
        Provider provider = UserClusterDataExtractor.getGroupProvider(cluster, ge.getName());
        boolean storagePreparation = (prepStoragesConf != null && prepStoragesConf.equalsIgnoreCase("true") 
            && (provider instanceof Ec2));
        if (storagePreparation) {
          String model = ((Ec2) provider).getType();
          InstanceType instanceType = InstanceType.valueByModel(model);
          PrepareStoragesTask st
              = new PrepareStoragesTask(me, clusterStats, submitter, instanceType.getStorageDevices());
          dag.addTask(st);
        }
        AptGetEssentialsTask t1 = new AptGetEssentialsTask(me, clusterStats, submitter, storagePreparation);
        InstallBerkshelfTask t2 = new InstallBerkshelfTask(me, clusterStats, submitter);
        MakeSoloRbTask t3 = new MakeSoloRbTask(me, vendorPath, clusterStats, submitter);
        dag.addTask(t1);
        dag.addTask(t2);
        dag.addTask(t3);

      }
    }
  }

}
