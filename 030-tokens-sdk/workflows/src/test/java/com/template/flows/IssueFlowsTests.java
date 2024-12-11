package com.template.flows;

import com.google.common.collect.ImmutableList;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import javafx.util.Pair;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.TransactionState;
import net.corda.core.transactions.SignedTransaction;
import net.corda.testing.node.MockNetwork;
import net.corda.testing.node.StartedMockNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static com.template.flows.FlowTestHelpers.*;
import static org.junit.Assert.*;

public class IssueFlowsTests {
    private final MockNetwork network;
    private final StartedMockNode alice;
    private final StartedMockNode bob;
    private final StartedMockNode carly;
    private final StartedMockNode dan;

    public IssueFlowsTests() throws Exception {
        network = new MockNetwork(prepareMockNetworkParameters());
        alice = network.createNode();
        bob = network.createNode();
        carly = network.createNode();
        dan = network.createNode();
    }

    @Before
    public void setup() {
        network.runNetwork();
    }

    @After
    public void tearDown() {
        network.stopNodes();
    }

    @Test
    public void signedTransactionPickedThePreferredNotary() throws Exception {
        final IssueFlows.Initiator flow = new IssueFlows.Initiator(bob.getInfo().getLegalIdentities().get(0), 10L);
        final CordaFuture<SignedTransaction> future = alice.startFlow(flow);
        network.runNetwork();

        final SignedTransaction tx = future.get();
        //noinspection ConstantConditions
        assertEquals("O=App Notary, L=London, C=GB", tx.getNotary().getName().toString());
    }

    @Test
    public void signedTransactionReturnedByTheFlowIsSignedByTheIssuer() throws Exception {
        final IssueFlows.Initiator flow = new IssueFlows.Initiator(bob.getInfo().getLegalIdentities().get(0), 10L);
        final CordaFuture<SignedTransaction> future = alice.startFlow(flow);
        network.runNetwork();

        final SignedTransaction tx = future.get();
        tx.verifyRequiredSignatures();
    }

    @Test
    public void flowRecordsATransactionInIssuerAndHolderTransactionStoragesOnly() throws Exception {
        final IssueFlows.Initiator flow = new IssueFlows.Initiator(bob.getInfo().getLegalIdentities().get(0), 10L);
        final CordaFuture<SignedTransaction> future = alice.startFlow(flow);
        network.runNetwork();
        final SignedTransaction tx = future.get();

        // We check the recorded transaction in both transaction storages.
        for (StartedMockNode node : ImmutableList.of(alice, bob)) {
            assertEquals(tx, node.getServices().getValidatedTransactions().getTransaction(tx.getId()));
        }
        for (StartedMockNode node : ImmutableList.of(carly, dan)) {
            assertNull(node.getServices().getValidatedTransactions().getTransaction(tx.getId()));
        }
    }

    @Test
    public void flowRecordsATransactionInIssuerAndBothHolderTransactionStorages() throws Exception {
        final IssueFlows.Initiator flow = new IssueFlows.Initiator(ImmutableList.of(
                new Pair<>(bob.getInfo().getLegalIdentities().get(0), 10L),
                new Pair<>(carly.getInfo().getLegalIdentities().get(0), 20L)));
        final CordaFuture<SignedTransaction> future = alice.startFlow(flow);
        network.runNetwork();
        final SignedTransaction tx = future.get();

        // We check the recorded transaction in transaction storages.
        for (StartedMockNode node : ImmutableList.of(alice, bob, carly)) {
            assertEquals(tx, node.getServices().getValidatedTransactions().getTransaction(tx.getId()));
        }
        assertNull(dan.getServices().getValidatedTransactions().getTransaction(tx.getId()));
    }

    @Test
    public void recordedTransactionHasNoInputsAndASingleOutputTheFungibleToken() throws Exception {
        final FungibleToken expected = createFrom(alice, bob, 10L);

        final IssueFlows.Initiator flow = new IssueFlows.Initiator(
                expected.getHolder(), expected.getAmount().getQuantity());
        final CordaFuture<SignedTransaction> future = alice.startFlow(flow);
        network.runNetwork();
        final SignedTransaction tx = future.get();

        // We check the recorded transaction in both vaults.
        for (StartedMockNode node : ImmutableList.of(alice, bob)) {
            final SignedTransaction recordedTx = node.getServices().getValidatedTransactions().getTransaction(tx.getId());
            assertNotNull(recordedTx);
            assertTrue(recordedTx.getTx().getInputs().isEmpty());
            final List<TransactionState<ContractState>> txOutputs = recordedTx.getTx().getOutputs();
            assertEquals(1, txOutputs.size());
            assertEquals(expected, txOutputs.get(0).getData());
        }
    }

    @Test
    public void thereIs1CorrectRecordedState() throws Exception {
        final FungibleToken expected = createFrom(alice, bob, 10L);

        final IssueFlows.Initiator flow = new IssueFlows.Initiator(
                expected.getHolder(), expected.getAmount().getQuantity());
        final CordaFuture<SignedTransaction> future = alice.startFlow(flow);
        network.runNetwork();
        future.get();

        // We check the recorded state in both vaults.
        assertHasStatesInVault(alice, ImmutableList.of(expected));
        assertHasStatesInVault(bob, ImmutableList.of(expected));
    }

    @Test
    public void recordedTransactionHasNoInputsAndManyOutputsTheFungibleTokens() throws Exception {
        final FungibleToken expected1 = createFrom(alice, bob, 10L);
        final FungibleToken expected2 = createFrom(alice, carly, 20L);

        final IssueFlows.Initiator flow = new IssueFlows.Initiator(ImmutableList.of(
                toPair(expected1),
                toPair(expected2)));
        final CordaFuture<SignedTransaction> future = alice.startFlow(flow);
        network.runNetwork();
        final SignedTransaction tx = future.get();

        // We check the recorded transaction in the 3 vaults.
        for (StartedMockNode node : ImmutableList.of(alice, bob, carly)) {
            final SignedTransaction recordedTx = node.getServices().getValidatedTransactions().getTransaction(tx.getId());
            assertNotNull(recordedTx);
            assertTrue(recordedTx.getTx().getInputs().isEmpty());
            final List<TransactionState<ContractState>> txOutputs = recordedTx.getTx().getOutputs();
            assertEquals(2, txOutputs.size());
            assertEquals(expected1, txOutputs.get(0).getData());
            assertEquals(expected2, txOutputs.get(1).getData());
        }
    }

    @Test
    public void thereAre2CorrectStatesRecordedByRelevance() throws Exception {
        final FungibleToken expected1 = createFrom(alice, bob, 10L);
        final FungibleToken expected2 = createFrom(alice, carly, 20L);

        final IssueFlows.Initiator flow = new IssueFlows.Initiator(ImmutableList.of(
                toPair(expected1),
                toPair(expected2)));
        final CordaFuture<SignedTransaction> future = alice.startFlow(flow);
        network.runNetwork();
        future.get();

        // We check the recorded state in the 4 vaults.
        assertHasStatesInVault(alice, ImmutableList.of(expected1, expected2));
        // Notice how bob did not save carly's state.
        assertHasStatesInVault(bob, ImmutableList.of(expected1));
        assertHasStatesInVault(carly, ImmutableList.of(expected2));
        assertHasStatesInVault(dan, Collections.emptyList());
    }

    @Test
    public void recordedTransactionHasNoInputsAnd2OutputsOfSameHolderTheFungibleTokens() throws Exception {
        final FungibleToken expected1 = createFrom(alice, bob, 10L);
        final FungibleToken expected2 = createFrom(alice, bob, 20L);

        final IssueFlows.Initiator flow = new IssueFlows.Initiator(ImmutableList.of(
                toPair(expected1),
                toPair(expected2)));
        final CordaFuture<SignedTransaction> future = alice.startFlow(flow);
        network.runNetwork();
        final SignedTransaction tx = future.get();

        // We check the recorded transaction in both vaults.
        for (StartedMockNode node : ImmutableList.of(alice, bob)) {
            final SignedTransaction recordedTx = node.getServices().getValidatedTransactions().getTransaction(tx.getId());
            assertNotNull(recordedTx);
            assertTrue(recordedTx.getTx().getInputs().isEmpty());
            final List<TransactionState<ContractState>> txOutputs = recordedTx.getTx().getOutputs();
            assertEquals(2, txOutputs.size());
            assertEquals(expected1, txOutputs.get(0).getData());
            assertEquals(expected2, txOutputs.get(1).getData());
        }
    }

    @Test
    public void thereAre2CorrectRecordedStatesAgain() throws Exception {
        final FungibleToken expected1 = createFrom(alice, bob, 10L);
        final FungibleToken expected2 = createFrom(alice, bob, 20L);

        final IssueFlows.Initiator flow = new IssueFlows.Initiator(ImmutableList.of(
                toPair(expected1),
                toPair(expected2)));
        final CordaFuture<SignedTransaction> future = alice.startFlow(flow);
        network.runNetwork();
        future.get();

        // We check the recorded state in the 4 vaults.
        assertHasStatesInVault(alice, ImmutableList.of(expected1, expected2));
        assertHasStatesInVault(bob, ImmutableList.of(expected1, expected2));
        assertHasStatesInVault(carly, Collections.emptyList());
        assertHasStatesInVault(dan, Collections.emptyList());
    }

}
