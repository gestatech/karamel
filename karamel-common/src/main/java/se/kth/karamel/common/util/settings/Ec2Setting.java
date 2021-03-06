package se.kth.karamel.common.util.settings;

import org.jclouds.aws.domain.Region;
import org.jclouds.ec2.domain.InstanceType;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Alberto on 2015-05-14.
 */
public enum Ec2Setting {

  PROVIDER_EC2_DEFAULT_TYPE(InstanceType.M1_MEDIUM),
  PROVIDER_EC2_DEFAULT_REGION(Region.EU_WEST_1),
  PROVIDER_EC2_DEFAULT_IMAGE("ami-0307ce74"),
  PROVIDER_EC2_DEFAULT_USERNAME("ubuntu"),
  EC2_GROUPNAME_PATTERN("[a-z0-9][[a-z0-9]|[-]]*");
  //12.04  "ami-896c96fe"; // 14.04
  //12.04  "ami-896c96fe"; // 14.04

  private static final Map<String, Ec2Setting> lookup
          = new HashMap<String, Ec2Setting>();

  static {
    for (Ec2Setting s : EnumSet.allOf(Ec2Setting.class))
      lookup.put(s.getParameter(), s);
  }

  private String parameter;

  private Ec2Setting(String parameter) {
    this.parameter = parameter;
  }

  public static Ec2Setting get(String parameter) {
    return lookup.get(parameter);
  }

  public String getParameter() {
    return parameter;
  }
}
