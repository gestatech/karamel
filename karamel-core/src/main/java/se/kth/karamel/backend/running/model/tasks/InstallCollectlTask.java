/*
* To change this license header, choose License Headers in Project Properties.
* To change this template file, choose Tools | Templates
* and open the template in the editor.
*/
package se.kth.karamel.backend.running.model.tasks;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.log4j.Logger;
import se.kth.karamel.backend.converter.ShellCommandBuilder;
import se.kth.karamel.backend.machines.TaskSubmitter;
import se.kth.karamel.backend.running.model.MachineRuntime;
import se.kth.karamel.common.stats.ClusterStats;
import se.kth.karamel.common.util.Settings;

/**
 *
 * @author kamal
 */
public class InstallCollectlTask extends Task {
  
  private static final Logger logger = Logger.getLogger(InstallCollectlTask.class);
  
  public InstallCollectlTask(String dagName, MachineRuntime machine, ClusterStats clusterStats, 
      TaskSubmitter submitter) {
    super(dagName, "install collectl", "install collectl", true, machine, clusterStats, submitter);
  }
  
  @Override
  public List<ShellCommand> getCommands() throws IOException {
    if (commands == null) {
      commands = ShellCommandBuilder.makeSingleFileCommand(Settings.SCRIPT_PATH_INSTALL_COLLECTL,
          "sudo_command", getSudoCommand(),
          "pid_file", Settings.PID_FILE_NAME,
          "task_id", getId(),
          "succeedtasks_filepath", Settings.SUCCEED_TASKLIST_FILENAME);
    }
    return commands;
  }
  
  public static String makeUniqueId(String machineId) {
    return "install collectl on " + machineId;
  }
  
  @Override
  public String uniqueId() {
    return makeUniqueId(super.getMachineId());
  }
  
  @Override
  public Set<String> dagDependencies() {
    Set<String> deps = new HashSet<>();
    String aptget = AptGetEssentialsTask.makeUniqueId(getMachineId());
    deps.add(aptget);
    return deps;
  }
}