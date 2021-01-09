package de.tum.ei.lkn.eces.nbi;

import de.tum.ei.lkn.eces.core.Component;
import de.tum.ei.lkn.eces.core.annotations.ComponentBelongsTo;

/**
 * Component representing the cookie for a tenant.
 *
 * @author Amaury Van Bemten
 */
@ComponentBelongsTo(system = NBISystem.class)
public class Cookie extends Component {
    private int cookie;

    public Cookie(int cookie) {
        this.cookie = cookie;
    }

    public int getCookie() {
        return cookie;
    }
}
