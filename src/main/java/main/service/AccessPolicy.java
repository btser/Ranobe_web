package main.service;

import main.entity.Account;
import main.entity.AccountRole;
import main.entity.Product;
import main.entity.Status;
import main.entity.User;
import main.repository.AccountRepository;
import org.springframework.stereotype.Service;

@Service
public class AccessPolicy {
    private final AccountRepository accounts;

    public AccessPolicy(AccountRepository accounts) {
        this.accounts = accounts;
    }

    public boolean isOwner(User viewer, Product product) {
        return viewer != null && product != null && product.getUploader() != null
                && viewer.getUserId() == product.getUploader().getUserId();
    }

    public boolean canModerate(User viewer) {
        if (viewer == null) return false;
        Account account = accounts.findByUserUserId(viewer.getUserId()).orElse(null);
        return account != null && (account.getRole() == AccountRole.ADMIN
                || account.getRole() == AccountRole.MOD);
    }

    public boolean canEdit(User viewer, Product product) {
        return isOwner(viewer, product);
    }

    public boolean canManage(User viewer, Product product) {
        return product != null && canModerate(viewer);
    }

    public boolean canView(User viewer, Product product) {
        if (product == null) return false;
        if (product.isDeleted()) return canManage(viewer, product);
        return product.getStatus() == Status.PUBLIC
                || isOwner(viewer, product) || canManage(viewer, product);
    }
}
