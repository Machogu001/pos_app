package co.ke.bremac.posapp.cart;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CartTest {
    @Test public void totalsFixedDiscountAndChange() {
        Cart cart = new Cart();
        cart.add(1, "Milk", "M1", 100);
        cart.add(1, "Milk", "M1", 100);
        cart.discountType = "fixed";
        cart.discountAmount = 30;
        Cart.Payment p = new Cart.Payment();
        p.amount = 200;
        cart.payments.add(p);
        assertEquals(200, cart.subtotal(2), 0.001);
        assertEquals(30, cart.discount(2), 0.001);
        assertEquals(170, cart.total(2), 0.001);
        assertEquals(30, cart.change(2), 0.001);
        assertEquals(0, cart.balance(2), 0.001);
    }

    @Test public void totalsPercentageDiscountAndRounding() {
        Cart cart = new Cart();
        cart.add(1, "Tea", "T1", 99.995);
        cart.discountType = "percentage";
        cart.discountAmount = 10;
        assertEquals(100.00, cart.subtotal(2), 0.001);
        assertEquals(10.00, cart.discount(2), 0.001);
        assertEquals(90.00, cart.total(2), 0.001);
    }
}
