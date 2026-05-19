package me.almana.pipezlagfix;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

public class TrackingItemHandler implements ResourceHandler<ItemResource> {
    private final ResourceHandler<ItemResource> delegate;
    private final SuccessJournal successJournal = new SuccessJournal();
    private boolean didExtract;

    public TrackingItemHandler(ResourceHandler<ItemResource> delegate) {
        this.delegate = delegate;
        this.didExtract = false;
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public ItemResource getResource(int index) {
        return delegate.getResource(index);
    }

    @Override
    public long getAmountAsLong(int index) {
        return delegate.getAmountAsLong(index);
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        return delegate.getCapacityAsLong(index, resource);
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return delegate.isValid(index, resource);
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return delegate.insert(index, resource, amount, transaction);
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        int extracted = delegate.extract(index, resource, amount, transaction);
        if (extracted > 0) {
            successJournal.updateSnapshots(transaction);
        }
        return extracted;
    }

    @Override
    public int extract(ItemResource resource, int amount, TransactionContext transaction) {
        int extracted = delegate.extract(resource, amount, transaction);
        if (extracted > 0) {
            successJournal.updateSnapshots(transaction);
        }
        return extracted;
    }

    private class SuccessJournal extends SnapshotJournal<Boolean> {
        @Override
        protected Boolean createSnapshot() {
            return Boolean.TRUE;
        }

        @Override
        protected void revertToSnapshot(Boolean snapshot) {
        }

        @Override
        protected void onRootCommit(Boolean snapshot) {
            didExtract = true;
        }
    }

    public boolean didExtract() {
        return didExtract;
    }
}
