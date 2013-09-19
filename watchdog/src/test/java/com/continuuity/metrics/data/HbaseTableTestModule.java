package com.continuuity.metrics.data;

import com.continuuity.data.table.OVCTableHandle;
import com.continuuity.metrics.guice.MetricsAnnotation;
import com.google.inject.AbstractModule;
import com.google.inject.Scopes;

/**
*
*/
final class HbaseTableTestModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(OVCTableHandle.class).annotatedWith(MetricsAnnotation.class).to(HBaseFilterableOVCTableHandle.class);
    bind(MetricsTableFactory.class).to(DefaultMetricsTableFactory.class).in(Scopes.SINGLETON);
  }
}
