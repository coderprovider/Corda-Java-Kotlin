package com.template.contracts;

import com.template.states.TokenState;
import com.template.states.TokenStateUtilities;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.CommandWithParties;
import net.corda.core.contracts.Contract;
import net.corda.core.identity.Party;
import net.corda.core.transactions.LedgerTransaction;
import org.jetbrains.annotations.NotNull;

import java.security.PublicKey;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static net.corda.core.contracts.ContractsDSL.requireSingleCommand;
import static net.corda.core.contracts.ContractsDSL.requireThat;

public final class TokenContract implements Contract {
    public static final String TOKEN_CONTRACT_ID = "com.template.contracts.TokenContract";

    @Override
    public void verify(@NotNull final LedgerTransaction tx) {
        final CommandWithParties<Commands> command = requireSingleCommand(tx.getCommands(), Commands.class);

        // This contract does not care about states it has no knowledge about.
        // This will be useful, for instance, when the token is exchanged in a trade.
        final List<TokenState> inputs = tx.inputsOfType(TokenState.class);
        final List<TokenState> outputs = tx.outputsOfType(TokenState.class);
        final boolean hasAllPositiveQuantities =
                inputs.stream().allMatch(it -> 0 < it.getQuantity()) &&
                        outputs.stream().allMatch(it -> 0 < it.getQuantity());
        final Set<PublicKey> allInputHolderKeys = inputs.stream()
                .map(it -> it.getHolder().getOwningKey())
                .collect(Collectors.toSet());

        if (command.getValue() instanceof Commands.Issue) {
            requireThat(req -> {
                // Constraints on the shape of the transaction.
                req.using("No tokens should be consumed, in inputs, when issuing.", inputs.isEmpty());
                req.using("There should be issued tokens, in outputs.", !outputs.isEmpty());

                // Constraints on the issued tokens themselves.
                req.using("All quantities must be above 0.", hasAllPositiveQuantities);

                // Constraints on the signers.
                req.using("The issuers should sign.",
                        command.getSigners().containsAll(outputs.stream()
                                .map(it -> it.getIssuer().getOwningKey())
                                .collect(Collectors.toSet())
                        ));
                // We assume the owners need not sign although they are participants.

                return null;
            });
        } else if (command.getValue() instanceof Commands.Move) {
            requireThat(req -> {
                // Constraints on the shape of the transaction.
                req.using("There should be tokens to move, in inputs.", !inputs.isEmpty());
                req.using("There should be moved tokens, in outputs.", !outputs.isEmpty());

                // Constraints on the redeemed tokens themselves.
                req.using("All quantities must be above 0.", hasAllPositiveQuantities);
                final Map<Party, Long> inputSums = TokenStateUtilities.mapSumByIssuer(inputs);
                final Map<Party, Long> outputSums = TokenStateUtilities.mapSumByIssuer(outputs);
                req.using("The list of issuers should be conserved.",
                        inputSums.keySet().equals(outputSums.keySet()));
                req.using("The sum of quantities for each issuer should be conserved.",
                        inputSums.entrySet().stream()
                                .allMatch(entry -> outputSums.get(entry.getKey()).equals(entry.getValue())));

                // Constraints on the signers.
                req.using("The current holders should sign.",
                        command.getSigners().containsAll(allInputHolderKeys));

                return null;
            });
        } else if (command.getValue() instanceof Commands.Redeem) {
            requireThat(req -> {
                // Constraints on the shape of the transaction.
                req.using("There should be tokens to redeem, in inputs.", !inputs.isEmpty());
                req.using("No tokens should be issued, in outputs, when redeeming.", outputs.isEmpty());

                // Constraints on the redeemed tokens themselves.
                req.using("All quantities must be above 0.", hasAllPositiveQuantities);

                // Constraints on the signers.
                req.using("The issuers should sign.",
                        command.getSigners().containsAll(inputs.stream()
                                .map(it -> it.getIssuer().getOwningKey())
                                .collect(Collectors.toSet())
                        ));
                req.using("The current holders should sign.",
                        command.getSigners().containsAll(allInputHolderKeys));

                return null;
            });
        } else {
            throw new IllegalArgumentException("Unknown command " + command.getValue());
        }
    }

    public interface Commands extends CommandData {
        class Issue implements Commands {
        }

        class Move implements Commands {
        }

        class Redeem implements Commands {
        }
    }
}