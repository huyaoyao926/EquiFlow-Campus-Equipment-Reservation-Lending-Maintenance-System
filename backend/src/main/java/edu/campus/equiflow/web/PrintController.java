package edu.campus.equiflow.web;

import edu.campus.equiflow.service.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class PrintController {
  private final ReservationService reservations;
  private final UserService users;

  public PrintController(ReservationService reservations, UserService users) {
    this.reservations = reservations;
    this.users = users;
  }

  @GetMapping("/api/reservations/{id}/receipt")
  String receipt(@PathVariable Long id, Authentication auth, Model model) {
    model.addAttribute("record", reservations.detail(id, users.current(auth)));
    return "receipt";
  }
}
