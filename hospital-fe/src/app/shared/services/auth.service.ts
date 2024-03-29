import { HttpClient } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { LoginRequest, LoginResponse } from "../model/auth.model";
import { Observable } from "rxjs";
import { User } from "../model/user.model";

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly LOGIN_URL = '/api/auth/login';
  private readonly LOGOUT_URL = '/api/auth/logout';
  private readonly CURRENT_USER_URL = '/api/user/info';

  constructor(private http: HttpClient) {}

  public login(loginRequest: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(this.LOGIN_URL, loginRequest);
  }

  public logout(): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(this.LOGOUT_URL, null);
  }

  public getCurrentUserInfo(): Observable<User> {
    return this.http.get<User>(`${this.CURRENT_USER_URL}`);
  }
}
