package co.ke.bremac.posapp;

/** The complete BreMac360 website (dashboard, reports, settings, …) for business admins. */
public class WebSystemActivity extends WebPosActivity {
    @Override
    protected String webTarget() {
        return "home";
    }

    @Override
    protected String screenName() {
        return "Full system";
    }

    @Override
    protected boolean allowed() {
        return session.permissions.isAdmin;
    }
}
