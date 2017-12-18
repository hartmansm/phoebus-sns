/*******************************************************************************
 * Copyright (c) 2017 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.phoebus.sns.completion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.phoebus.framework.autocomplete.Proposal;
import org.phoebus.framework.rdb.RDBConnectionPool;
import org.phoebus.framework.spi.PVProposalProvider;

/** PV name completion for SNS
 *  @author Kay Kasemir
 */
@SuppressWarnings("nls")
public class SNSPVProposals implements PVProposalProvider
{
    public static final String NAME = "SNS PVs";

    /** URL, user, pass, URL, user, pass, ... */
    private static final List<String> infos = List.of
    (
        "jdbc:oracle:thin:@(DESCRIPTION=(LOAD_BALANCE=OFF)(FAILOVER=ON)" +
        "(ADDRESS=(PROTOCOL=TCP)(HOST=snsappa.sns.ornl.gov)(PORT=1610))" +
        "(ADDRESS=(PROTOCOL=TCP)(HOST=snsappb.sns.ornl.gov)(PORT=1610))" +
        "(CONNECT_DATA=(SERVICE_NAME=prod_controls)))",
        "sns_reports",
        "sns",
        "jdbc:oracle:thin:@snsoroda-scan.sns.gov:1521/scprod_controls",
        "sns_reports",
        "sns"
    );

    private final List<RDBConnectionPool> pools;

    public SNSPVProposals()
    {
        pools = new ArrayList<>(infos.size() / 3);
        for (int i=0; i<infos.size(); i+=3)
            try
            {
                final RDBConnectionPool pool = new RDBConnectionPool(infos.get(i), infos.get(i+1), infos.get(i+2));
                pool.setTimeout(10);
                pools.add(pool);
            }
            catch (Exception ex)
            {
                Logger.getLogger(getClass().getPackageName())
                      .log(Level.WARNING, "Cannot create connection pool", ex);
            }
    }

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public List<Proposal> lookup(final String text)
    {
        // Need some minimum character count to limit size of result
        if (text.length() < 3)
            return List.of();

        final List<Proposal> result = new ArrayList<>();
        for (RDBConnectionPool pool : pools)
            try
            {
                final Connection connection = pool.getConnection();
                try
                {
                    final PreparedStatement stmt = connection.prepareStatement("SELECT name FROM chan_arch.channel WHERE name LIKE ? FETCH FIRST 30 ROWS ONLY");
                    stmt.setString(1, "%" + text + "%");
                    final ResultSet rs = stmt.executeQuery();
                    while (rs.next())
                        result.add(new Proposal(rs.getString(1)));
                    rs.close();
                    stmt.close();
                }
                finally
                {
                    pool.releaseConnection(connection);
                }
            }
            catch (Exception ex)
            {   // Ignore interruptions (new lookup replaced this one)
                if (! Thread.currentThread().isInterrupted())
                    Logger.getLogger(getClass().getPackageName())
                          .log(Level.WARNING, "Cannot search for PV names", ex);
            }

        return result;
    }
}