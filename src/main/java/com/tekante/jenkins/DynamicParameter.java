package com.tekante.jenkins;

import hudson.Extension;

import hudson.model.Hudson;
import hudson.model.ParameterValue;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterValue;
import hudson.util.ListBoxModel;
import hudson.model.Job;

import java.util.List;
import java.util.logging.Logger;
import java.io.*;

import net.sf.json.JSONObject;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.lf5.LogLevel;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.UnsupportedEncodingException;

/**
 * String based parameter that supports picking the string from two lists of values
 * presented at build time generated from data specified in job configuration
 * for this parameter and organized such that the command for the second list sees
 * the value from the first list and can change allowed values dynamically
 *
 * @author Chris Johnson
 * @see {@link ParameterDefinition}
 */

public class DynamicParameter extends ParameterDefinition {
  private static final Logger LOG = Logger.getLogger(DynamicParameter.class.getName());
  static final long serialVersionUID = 4;
  public String value = "";
  public String dynamicValue = "";
  public String valueOptions;
  public String defaultValue;
  public String dynamicValueOptions;
  public String secondName;
  public String filePath;

  @DataBoundConstructor
  public DynamicParameter(String name, String description, String valueOptions, String defaultValue, String dynamicValueOptions, String secondName, String filePath ) {
    super(name, description);
    this.secondName = secondName;
    this.valueOptions = valueOptions;
    this.defaultValue = defaultValue;
    this.dynamicValueOptions = dynamicValueOptions;
    this.filePath = filePath;
  }

  @Extension
  public static final class DescriptorImpl extends ParameterDescriptor {
    @Override
    public String getDisplayName() {
      return "Dynamic Parameter";
    }

    private DynamicParameter getDynamicParameter(String param) {
      String containsJobName = getCurrentDescriptorByNameUrl();
      String jobName = null;
      try {
        jobName = java.net.URLDecoder.decode(containsJobName.substring(containsJobName.lastIndexOf("/") + 1), "UTF-8");
      } catch (UnsupportedEncodingException e) {
        LOG.warning("Could not find parameter definition instance for parameter " + param + " due to encoding error in job name: " + e.getMessage());
        return null;
      }

      Job<?, ?> j = Hudson.getInstance().getItemByFullName(jobName, hudson.model.Job.class);
      if (j != null) {
        ParametersDefinitionProperty pdp = j.getProperty(hudson.model.ParametersDefinitionProperty.class);
        List<ParameterDefinition> pds = pdp.getParameterDefinitions();
        for (ParameterDefinition pd : pds) {
          if (this.isInstance(pd) && ((DynamicParameter) pd).getName().equalsIgnoreCase(param)) {
            return (DynamicParameter) pd;
          }
        }
      }
      LOG.warning("Could not find parameter definition instance for parameter " + param);
      return null;
    }

    public ListBoxModel doFillValueItems(@QueryParameter String name) {
      LOG.finer("Called with param: " + name);
      ListBoxModel m = new ListBoxModel();

      DynamicParameter dp = this.getDynamicParameter(name);
      if (dp != null) {
        for (String s : dp.valueOptions.split("\\r?\\n")) {
          m.add(s);
        }
      }
      return m;
    }

    public ListBoxModel doFillDynamicValueItems(@QueryParameter String name, @QueryParameter String value) throws IOException {
      ListBoxModel m = new ListBoxModel();
              
      DynamicParameter dp = this.getDynamicParameter(name);
      String finalValueOptions = "";
      
      // If a file is specified, read it and dump it into finalValueOptions
      if( !dp.filePath.isEmpty() )
      {
    	  FileInputStream inputStream = new FileInputStream( dp.filePath );
    	    try {
    	        finalValueOptions = IOUtils.toString(inputStream);
    	    } finally {
    	        inputStream.close();
    	    }    	        	     	  
      }
      else if (dp != null) {
    	  finalValueOptions = dp.dynamicValueOptions;
      }
      for (String s : finalValueOptions.split("\\r?\\n")) {
          if ( s.indexOf(value) == 0 ) {
        	  if( !s.startsWith("#")) {		// Avoid the comment lines        		          
	            String[] str = s.split(":");
	            // TODO: More input checking
	            m.add(str[1]);
        	  }
          }
      }  
      return m;
    }
  }

  @Override
  public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
    DynamicParameterValue value = req.bindJSON(DynamicParameterValue.class, jo);
    return value;
  }

  @Override
  public ParameterValue createValue(StaplerRequest req) {
    String[] value = req.getParameterValues(getName());
    String[] dynamicValue = req.getParameterValues(this.secondName);
    LOG.warning(getName() + ": " + value[0] + "\n");
    LOG.warning(this.secondName + ": " + dynamicValue[0] + "\n");
    return new DynamicParameterValue(getName(), value[0], this.secondName, dynamicValue[0]);
  }
}