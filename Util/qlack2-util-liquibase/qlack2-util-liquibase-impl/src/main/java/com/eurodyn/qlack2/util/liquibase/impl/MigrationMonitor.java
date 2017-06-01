package com.eurodyn.qlack2.util.liquibase.impl;

import com.eurodyn.qlack2.util.liquibase.api.MigrationExecutionStrategy;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sql.DataSource;
import org.apache.karaf.jndi.JndiService;
import org.ops4j.pax.cdi.api.OsgiService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.startlevel.FrameworkStartLevel;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

import java.text.MessageFormat;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Monitors the environment in which migrations take place for specific conditions.
 */
@Singleton
public class MigrationMonitor {

  /**
   * JUL reference
   */
  private final static Logger LOGGER = Logger.getLogger(MigrationMonitor.class.getName());

  /**
   * A reference to the settings of this component
   */
  @Inject
  private Settings settings;

  /**
   * The BundleContext in which this thread runs, so that references to the
   * OSGi framework can be resolved.
   */
  @Inject
  private BundleContext bundleContext;

  /**
   * A reference to the JNDI service in order to lookup for the user-defined
   * datasource.
   */
  @OsgiService
  @Inject
  private JndiService jndiService;

  private MigrationMonitoringDSThread migrationMonitoringDSThread;
  private MigrationMonitoringSLThread migrationMonitoringSLThread;

  /**
   * Component's default bootstrap logic in which threads are fired up
   * to check for preconditions.
   */
  @PostConstruct
  public void init() {
    migrationMonitoringDSThread = new MigrationMonitoringDSThread(settings, jndiService,
      bundleContext);
    migrationMonitoringDSThread.start();

    if (settings.getMigrationExecutionStrategy() == MigrationExecutionStrategy.START_LEVEL) {
      migrationMonitoringSLThread = new MigrationMonitoringSLThread(settings, bundleContext);
      migrationMonitoringSLThread.start();
    }
  }

  /**
   * Component's shutdown logic, properly terminating previously fired-up threads.
   */
  @PreDestroy
  public void shutdown() throws InterruptedException {
    LOGGER.log(Level.FINE, "Shutting down MigrationMonitor.");
    if (migrationMonitoringDSThread.isAlive()) {
      migrationMonitoringDSThread.terminate();
      migrationMonitoringDSThread.join();
    }

    if (migrationMonitoringSLThread != null && migrationMonitoringSLThread.isAlive()) {
      migrationMonitoringSLThread.terminate();
      migrationMonitoringSLThread.join();
    }
  }
}

/**
 * Monitor thread for the framework's current start-level.
 */
class MigrationMonitoringSLThread extends Thread {

  /**
   * JUL reference
   */
  private final static Logger LOGGER = Logger
    .getLogger(MigrationMonitoringSLThread.class.getName());

  /**
   * A reference to the settings of this component
   */
  private Settings settings;

  /**
   * The BundleContext to be able to access OSGi framework resources
   */
  private BundleContext bundleContext;

  /**
   * A delay (in msec) between successive loop-wait executions
   */
  private final long loopDelay = 1000;

  /**
   * A flag to terminate this thread when requested
   */
  private boolean terminate = false;

  MigrationMonitoringSLThread(Settings Settings, BundleContext bundleContext) {
    this.settings = Settings;
    this.bundleContext = bundleContext;
  }

  private int getCurrentStartLevel() {
    return (bundleContext.getBundle(0)
      .adapt(FrameworkStartLevel.class)).getStartLevel();
  }

  void terminate() {
    terminate = true;
  }

  public void run() {
    LOGGER.log(Level.FINE, "Starting MigrationMonitoringSLThread for: SL{0}.",
      settings.getBootCompleteSL());
    long startTime = Instant.now().toEpochMilli();
    int currentStartLevel = getCurrentStartLevel();
    while (!terminate && currentStartLevel < settings.getBootCompleteSL()
      && Instant.now().toEpochMilli() - startTime < settings.getMaxWaitForSL()) {
      MigrationExecutor.setCurrentStartLevel(currentStartLevel);
      try {
        Thread.sleep(loopDelay);
      } catch (InterruptedException e) {
        LOGGER.log(Level.SEVERE, e.getMessage(), e);
      }
      currentStartLevel = getCurrentStartLevel();
    }
    MigrationExecutor.setCurrentStartLevel(currentStartLevel);

    if (currentStartLevel < settings.getBootCompleteSL()) {
      MigrationAgent.currentThread().interrupt();
    }

    LOGGER.log(Level.FINE, "MigrationMonitoringSLThread thread terminated on: SL{0}.",
      MigrationExecutor.getCurrentStartLevel());
  }
}

/**
 * Monitor thread for the injection of a datasource.
 */
class MigrationMonitoringDSThread extends Thread {

  /**
   * JUL reference
   */
  private final static Logger LOGGER = Logger
    .getLogger(MigrationMonitoringDSThread.class.getName());

  /**
   * The BundleContext to be able to access OSGi framework resources
   */
  private final BundleContext bundleContext;

  /**
   * A delay (in msec) between successive loop-wait executions
   */
  private final long loopDelay = 1000;

  /**
   * A flag to terminate this thread when requested
   */
  private boolean terminate = false;

  /**
   * A reference to the settings of this component
   */
  private final Settings settings;

  /**
   * A reference to the JNDI service in order to lookup for the user-defined
   * datasource.
   */
  private final JndiService jndiService;

  MigrationMonitoringDSThread(final Settings settings, final JndiService jndiService,
    final BundleContext bundleContext) {
    this.settings = settings;
    this.jndiService = jndiService;
    this.bundleContext = bundleContext;
  }

  void terminate() {
    terminate = true;
  }

  public void run() {
    String jndiDS = "osgi:service/" + settings.getDatasource();
    LOGGER.log(Level.FINE, "Starting MigrationMonitoringDSThread for: {0}.", jndiDS);

    ServiceTracker serviceTracker = null;
    try {
      serviceTracker = new ServiceTracker(bundleContext, bundleContext.createFilter(
        MessageFormat.format("(dataSourceName={0})", settings.getDatasource())), new
        ServiceTrackerCustomizer() {
          @Override
          public Object addingService(ServiceReference serviceReference) {
            MigrationExecutor
              .setDataSource((DataSource) bundleContext.getService(serviceReference));
            return serviceReference;
          }

          @Override
          public void modifiedService(ServiceReference serviceReference, Object o) {
          }

          @Override
          public void removedService(ServiceReference serviceReference, Object o) {
          }
        });
    } catch (InvalidSyntaxException e) {
    }
    serviceTracker.open();

    /** Keep looping to refresh the Datasource in case it changes */
    while(!terminate) {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
      }
    }

    serviceTracker.close();
    LOGGER.log(Level.FINE, "MigrationMonitoringDSThread terminated.");
  }
}
